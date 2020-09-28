package ammonite.kernel

import ammonite.kernel.kernel._
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.Settings

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
  def process(code: String): Either[Seq[LogError], SuccessfulEvaluation] = {
//    val parsed: Either[LogError, Seq[String]] = {
//      parse(code, Parsers.splitter(_), verboseFailures = true) match {
//        case Parsed.Success(statements, _) => Right(statements)
//        case f: Parsed.Failure => Left(LogError(f.longMsg))
//      }
//    }
//
//    postParse(parsed)

    processBlock(code)
  }

  /** Reads and evaluates the supplied code as a monolithic block
    *
    * @param code code block to be evaluated
    *
    * @author Harshad Deo
    * @since 0.2.3
    */
  def processBlock(code: String): Either[Seq[LogError], SuccessfulEvaluation] =
    postParse(Right(Seq(code)))

  private def postParse(parsed: Either[LogError, Seq[String]]): Either[Seq[LogError], SuccessfulEvaluation] =
    lock.synchronized {
      val evaluationIndex = idxCtr.getAndIncrement

      val res = parsed.left.map(Seq(_)).map { statements =>
        val indexedWrapperName = Name(s"cmd${evaluationIndex}")
        val wrapperName = Seq(Name(s"$sessionNameStr"), indexedWrapperName)

        val munged: Either[Seq[LogError], MungedOutput] = Munger(
          statements,
          s"${evaluationIndex}",
          Seq(Name(s"$sessionNameStr")),
          indexedWrapperName,
          state.imports,
          state.compiler.parse
        )

        munged.flatMap { processed =>
          val compilationResult: CompilerOutput = state.compiler.compile(
            processed.code.getBytes(StandardCharsets.UTF_8),
            processed.prefixCharLength,
            s"_ReplKernel${evaluationIndex}.sc")

          compilationResult.flatMap {
            case (info, warning, classFiles, imports) =>
              val loadedClass: Either[LogError, Class[_]] = {
                scala.util
                  .Try {
                    for ((name, bytes) <- classFiles.sortBy(_._1)) {
                      state.frame.classloader.addClassFile(name, bytes)
                    }
                    Class.forName(s"$sessionNameStr." + indexedWrapperName.backticked, true, state.frame.classloader)
                  }
                  .toEither
                  .left
                  .map(LogMessage.fromThrowable)
              }

              val processed: Either[LogError, (Imports, Any)] =
                loadedClass.flatMap { cls =>
                  val evaluated: Either[LogError, Any] =
                    scala.util
                      .Try {
                        Option(cls.getDeclaredMethod(s"$generatedMain").invoke(null)).getOrElse(())
                      }
                      .toEither
                      .left
                      .map(LogMessage.fromThrowable)

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

              mapped.left.map(Seq(_))
          }
        }

      }

      // state mutation
      res.flatMap { validation =>
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
  )

  private[ammonite] def defaultSettings = new Settings()

  /** Generates a new instance
    *
    * @param settings scalac settings
    *
    * @author Harshad Deo
    * @since 0.1
    */
  def apply(settings: Settings = defaultSettings): ReplKernel = {

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

    new ReplKernel(genState(Imports(), initialClasspath, dynamicClasspath, settings, special))
  }

  private def genState(
      imports: Imports,
      initialClasspath: Seq[File],
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

    KernelState(new Frame(classLoader, initialClasspath), imports, compiler, pressy, dynamicClasspath)
  }

}
