package ammonite.kernel

import annotation.tailrec
import collection.mutable
import coursier._
import coursier.core.Repository
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import kernel._
import reflect.io.VirtualDirectory
import scalaz.concurrent.Task
import scalaz.{Name => _, _}
import Scalaz._
import tools.nsc.Settings
import Validation.FlatMap._
import fastparse.{Parsed, _}

/** Implements runtime code compilation and execution
  *
  * @author Harshad Deo
  * @since 0.1
  */
final class ReplKernel private (private[this] var state: ReplKernel.KernelState) {

  private[this] val sessionNameStr =
    s"_${UUID.randomUUID().toString.replace("-", "")}_"
  private[this] val lock = new AnyRef
  private val idxCtr = new AtomicInteger(0)

  /** Reads and evaluates the supplied code
    *
    * @param code code block to be evaluated
    *
    * @author Harshad Deo
    * @since 0.1
    */
  def process(code: String): Option[ValidationNel[LogError, SuccessfulEvaluation]] = {

    val parsed: Option[Validation[LogError, NonEmptyList[String]]] = {
      parse(code, Parsers.splitter(_), verboseFailures = true) match {
        case Parsed.Success(statements, _) =>
          statements.toList match {
            case h :: t =>
              val nel = NonEmptyList(h, t: _*)
              Some(Validation.success(nel))
            case Nil => None
          }
        case f: Parsed.Failure =>
          Some(Validation.failure(LogError(f.longMsg)))
      }
    }

    postParse(parsed)

  }

  /** Reads and evaluates the supplied code as a monolithic block
    *
    * @param code code block to be evaluated
    *
    * @author Harshad Deo
    * @since 0.2.3
    */
  def processBlock(code: String): Option[ValidationNel[LogError, SuccessfulEvaluation]] =
    postParse(Some(Success(NonEmptyList(code))))

  private def postParse(parsed: Option[Validation[LogError, NonEmptyList[String]]])
    : Option[ValidationNel[LogError, SuccessfulEvaluation]] =
    lock.synchronized {

      val evaluationIndex = idxCtr.getAndIncrement

      val res = parsed map { validation =>
        val validationNel = validation.toValidationNel

        validationNel flatMap { statements =>
          val indexedWrapperName = Name(s"cmd${evaluationIndex}")
          val wrapperName = Seq(Name(s"$sessionNameStr"), indexedWrapperName)

          val munged: ValidationNel[LogError, MungedOutput] = Munger(
            statements,
            s"${evaluationIndex}",
            Seq(Name(s"$sessionNameStr")),
            indexedWrapperName,
            state.imports,
            state.compiler.parse
          )

          munged flatMap { processed =>
            val compilationResult = state.compiler.compile(
              processed.code.getBytes(StandardCharsets.UTF_8),
              processed.prefixCharLength,
              s"_ReplKernel${evaluationIndex}.sc")

            compilationResult flatMap {
              case (info, warning, classFiles, imports) =>
                val loadedClass: Validation[LogError, Class[_]] = Validation
                  .fromTryCatchNonFatal {
                    for ((name, bytes) <- classFiles.sortBy(_._1)) {
                      state.frame.classloader.addClassFile(name, bytes)
                    }
                    Class.forName(s"$sessionNameStr." + indexedWrapperName.backticked, true, state.frame.classloader)
                  } leftMap (LogMessage.fromThrowable(_))

                val processed: Validation[LogError, (Imports, Any)] = loadedClass flatMap { cls =>
                  val evaluated: Validation[LogError, Any] = Validation
                    .fromTryCatchNonFatal {
                      Option(cls.getDeclaredMethod(s"$generatedMain").invoke(null))
                        .getOrElse(())
                    } leftMap (LogMessage.fromThrowable(_))

                  // maybe something here

                  val newImports = Imports(
                    for (id <- imports.value) yield {
                      val filledPrefix =
                        if (id.prefix.isEmpty) {
                          wrapperName
                        } else {
                          id.prefix
                        }
                      val rootedPrefix: Seq[Name] =
                        if (filledPrefix.headOption.exists(_.backticked == rootStr)) {
                          filledPrefix
                        } else {
                          Seq(Name(rootStr)) ++ filledPrefix
                        }

                      id.copy(prefix = rootedPrefix)
                    }
                  )
                  evaluated map ((newImports, _))
                }

                val mapped = processed map (x => (info, warning, x._1, x._2))

                mapped.toValidationNel
            }
          }

        } leftMap (_.reverse)
      }

      // state mutation
      res map { validation =>
        validation map {
          case (info, warning, newImports, value) =>
            state = state.copy(imports = state.imports ++ newImports)
            SuccessfulEvaluation(value, info, warning)
        }
      }
    }

  /** Provides semantic autocompletion at the indicated position, in the context of the current classpath and
    * previously evaluated expressions
    *
    * @author Harshad Dep
    * @since 0.1
    */
  def complete(text: String, position: Int): AutocompleteOutput =
    lock.synchronized {
      state.pressy.complete(text, position, Munger.importBlock(state.imports))
    }

  /** Adds a dependency on an external library using maven coordinates
    *
    * @author Harshad Deo
    * @since 0.1
    */
  def loadIvy(groupId: String, artifactId: String, version: String): ValidationNel[LogError, Unit] =
    lock.synchronized {
      val start = Resolution(
        Set(
          Dependency(Module(groupId, artifactId), version)
        )
      )
      val fetch = Fetch.from(state.repositories, Cache.fetch())
      val resolution = start.process.run(fetch).unsafePerformSync

      resolution.errors.toList match {
        case h :: t =>
          def forDependency(dependency: (Dependency, Seq[String])): LogError = {
            val str =
              s"${dependency._1.module.organization} :: ${dependency._1.module.name} :: ${dependency._1.version}"
            val msg = s"$str: ${dependency._2.mkString(", ")}"
            LogError(msg)
          }
          Failure(NonEmptyList(forDependency(h), (t map (forDependency(_))): _*))
        case Nil =>
          val localArtifacts: ValidationNel[LogError, Seq[File]] = Task
            .gatherUnordered(
              resolution.artifacts.map(Cache.file(_).run)
            )
            .unsafePerformSync
            .map(_.validationNel)
            .foldLeft(Seq.empty[File].successNel[FileError]) {
              case (acc, v) => (acc |@| v)(_ :+ _)
            }
            .leftMap(x => x map (y => LogError(y.describe)))

          localArtifacts map { jars =>
            state.frame.addClasspath(jars)
            state = ReplKernel.genState(
              state.imports,
              state.frame.classpath,
              state.repositories,
              state.dynamicClasspath,
              state.compiler.settings,
              state.frame.classloader)
          }
      }
    }

  /** Adds external repository used during subsequent evaluations
    *
    * @author Harshad Deo
    * @since 0.1
    */
  def addRepository(repository: Repository): Unit = lock.synchronized {
    state = state.copy(repositories = (repository :: state.repositories))
  }

}

object ReplKernel {

  private class Frame(val classloader: AmmoniteClassLoader, private[this] var classpath0: Seq[File]) {
    def classpath: Seq[File] = classpath0
    def addClasspath(additional: Seq[File]): Unit = {
      additional.map(_.toURI.toURL).foreach(classloader.add)
      classpath0 = classpath0 ++ additional
    }
  }

  private case class KernelState(
      frame: Frame,
      imports: Imports,
      compiler: Compiler,
      pressy: Pressy,
      dynamicClasspath: VirtualDirectory,
      repositories: List[Repository])

  private[ammonite] def defaultSettings = new Settings()
  private[ammonite] val defaultRepositories =
    List(Cache.ivy2Local, MavenRepository("https://repo1.maven.org/maven2"))

  /** Generates a new instance
    *
    * @param settings scalac settings
    * @param repositories list of repositories to be used during dynamic dependency resolution
    *
    * @author Harshad Deo
    * @since 0.1
    */
  def apply(settings: Settings = defaultSettings, repositories: List[Repository] = defaultRepositories): ReplKernel = {

    val currentClassLoader = Thread.currentThread().getContextClassLoader
    val hash = AmmoniteClassLoader.initialClasspathSignature(currentClassLoader)
    def special = new AmmoniteClassLoader(currentClassLoader, hash)

    val initialClasspath: List[File] = {
      val res = mutable.ListBuffer[File]()
      res.appendAll(
        System
          .getProperty("sun.boot.class.path")
          .split(java.io.File.pathSeparator)
          .map(new java.io.File(_))
      )

      @tailrec
      def go(classLoader: ClassLoader): Unit =
        if (classLoader == null) {
          ()
        } else {
          classLoader match {
            case t: URLClassLoader =>
              res.appendAll(t.getURLs.map(u => new File(u.toURI)))
            case _ => ()
          }
          go(classLoader.getParent)
        }

      go(currentClassLoader)

      res.toList.filter(_.exists)
    }

    val dynamicClasspath = new VirtualDirectory("(memory)", None)

    new ReplKernel(genState(Imports(), initialClasspath, repositories, dynamicClasspath, settings, special))
  }

  private def genState(
      imports: Imports,
      initialClasspath: Seq[File],
      repositories: List[Repository],
      dynamicClasspath: VirtualDirectory,
      settings: Settings,
      classLoader: => AmmoniteClassLoader): KernelState = {

    val compiler = new Compiler(initialClasspath, dynamicClasspath, classLoader, classLoader, settings.copy())
    val pressy = Pressy(
      initialClasspath,
      dynamicClasspath,
      settings.copy(),
      classLoader
    )

    KernelState(new Frame(classLoader, initialClasspath), imports, compiler, pressy, dynamicClasspath, repositories)
  }

}
