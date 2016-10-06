package ammonite.kernel

import collection.JavaConverters._
import Compiler._
import java.io.{OutputStream, File}
import java.util.zip.ZipFile
import kernel._
import language.existentials
import reflect.internal.util.Position
import reflect.io.{VirtualDirectory, VirtualFile, FileZipArchive, PlainDirectory, Directory, AbstractFile}
import scalaz._
import Scalaz._
import tools.nsc.{Global, Settings}
import tools.nsc.backend.JavaPlatform
import tools.nsc.interactive.Response
import tools.nsc.plugins.Plugin
import tools.nsc.reporters.StoreReporter
import tools.nsc.util.ClassPath.JavaContext
import tools.nsc.util._
import util.Try
import Validation.FlatMap._

/**
  * Encapsulates (almost) all the ickiness of Scalac so it doesn't leak into
  * the rest of the codebase. Makes use of a good amount of mutable state
  * for things like the log-output-forwarder or compiler-plugin-output because
  * These things are hard-coded into Scalac and can't be passed in from run to
  * run.
  *
  * Turns source-strings into the bytes of classfiles, possibly more than one
  * classfile per source-string (e.g. inner classes, or lambdas). Also lets
  * you query source strings using an in-built presentation compiler
  */
private[kernel] final class Compiler(classpath: Seq[java.io.File],
                                     dynamicClasspath: VirtualDirectory,
                                     evalClassloader: => ClassLoader,
                                     pluginClassloader: => ClassLoader,
                                     val settings: Settings){

  private[this] val lock = new AnyRef

  private[this] var importsLen = 0
  private[this] var lastImports = Seq.empty[ImportData]

  private[this] val pluginXml = "scalac-plugin.xml"
  private[this] lazy val plugins0 = {
    val loader = pluginClassloader

    val urls = loader.getResources(pluginXml).asScala.toVector

    val plugins = for {
      url <- urls
      elem = scala.xml.XML.load(url.openStream())
      name = (elem \\ pluginStr \ "name").text
      className = (elem \\ pluginStr \ "classname").text
      if name.nonEmpty && className.nonEmpty
      classOpt = Try(Some(loader.loadClass(className))).getOrElse(None)
    } yield (name, className, classOpt)

    val notFound = plugins.collect {
      case (name, className, None) => (name, className)
    }
    if (notFound.nonEmpty) {
      for ((name, className) <- notFound.sortBy(_._1))
        println(s"Implementation $className of plugin $name not found.")
    }

    plugins.collect { case (name, _, Some(cls)) => name -> cls }
  }

  private[this] val (vd, jcp) = initGlobalBits(
    classpath,
    dynamicClasspath,
    settings
  )

  private[this] val compiler = {
    val scalac = new Global(settings) { g =>
      override lazy val plugins = List(new AmmonitePlugin(g, lastImports = _, importsLen)) ++ {
        def isCompatible(plugin: Plugin, name: String): Boolean =
          util.Try {
            CompilerCompatibility.pluginInit(plugin, Nil, g.globalError)
          }.getOrElse {
            println(s"Warning: disabling plugin $name, initialization failed")
            false
          }
        for {
          (name, cls) <- plugins0
          plugin = Plugin.instantiate(cls, g)
          if isCompatible(plugin, name)
        } yield plugin
      }

      // Actually jcp, avoiding a path-dependent type issue in 2.10 here
      override def classPath = platform.classPath
      override lazy val platform: ThisPlatform = new JavaPlatform {
        val global: g.type = g
        override def classPath = jcp
      }
      override lazy val analyzer =
        CompilerCompatibility.analyzer(g, evalClassloader)
    }
    // Initialize scalac to the parser phase immediately, so we can start
    // using Compiler#parse even if we haven't compiled any compilation
    // units yet due to caching
    val run = new scalac.Run()
    scalac.phase = run.parserPhase
    run.cancel()
    scalac
  }

  /**
    * Compiles a blob of bytes and spits of a list of classfiles
    * importsLen0 is the length of topWrapper appended above code by wrappedCode function
    * It is passed to AmmonitePlugin to decrease this much offset from each AST node
    * corresponding to the actual code so as to correct the line numbers in error report
    */
  def compile(src: Array[Byte], importsLen0: Int, fileName: String): CompilerOutput = lock.synchronized {

    this.importsLen = importsLen0
    val singleFile = makeFile(src, fileName)

    val reporter = new StoreReporter
    compiler.reporter = reporter

    val run = new compiler.Run()
    vd.clear()
    val compilationResult = Validation.fromTryCatchNonFatal(run.compileFiles(List(singleFile)))

    val compilationResultMapped = compilationResult leftMap (LogMessage.fromThrowable(_))

    compilationResultMapped.toValidationNel flatMap { _ =>
      val outputFiles = enumerateVdFiles(vd).toVector

      val (errorMessages, warningMessages, infoMessages) =
        reporter.infos.foldLeft((List[LogError](), List[LogWarning](), List[LogInfo]())) {
          case ((error, warning, info), reporter.Info(pos, msg, reporter.ERROR)) =>
            (LogError(Position.formatMessage(pos, msg, false)) :: error, warning, info)
          case ((error, warning, info), reporter.Info(pos, msg, reporter.WARNING)) =>
            (error, LogWarning(Position.formatMessage(pos, msg, false)) :: warning, info)
          case ((error, warning, info), reporter.Info(pos, msg, reporter.INFO)) =>
            (error, warning, LogInfo(Position.formatMessage(pos, msg, false)) :: info)
        }

      (errorMessages) match {
        case h :: t =>
          val errorNel = NonEmptyList(h, t: _*)
          Failure(errorNel)
        case Nil =>
          //shutdownPressy()
          val files = for (x <- outputFiles if x.name.endsWith(classStr)) yield {
            val segments = x.path.split("/").toList.tail
            val output = writeDeep(dynamicClasspath, segments, "")
            output.write(x.toByteArray)
            output.close()
            (x.path.stripPrefix("(memory)/").stripSuffix(classStr).replace('/', '.'), x.toByteArray)
          }

          val imports = lastImports.toList
          Success((infoMessages, warningMessages, files, Imports(imports)))
      }
    }
  }

  def parse(line: String): ValidationNel[LogError, Seq[Global#Tree]] = lock.synchronized {
    val reporter = new StoreReporter
    compiler.reporter = reporter
    val parser = compiler.newUnitParser(line)
    val trees = CompilerCompatibility.trees(compiler)(parser)
    val errors: List[LogError] = reporter.infos.toList collect {
      case reporter.Info(pos, msg, reporter.ERROR) => LogError(Position.formatMessage(pos, msg, false))
    }
    (errors) match {
      case h :: t =>
        val errorNel = NonEmptyList(h, t: _*)
        Failure(errorNel)
      case Nil =>
        Success(trees)
    }
  }

}

private[kernel] object Compiler {

  private val pluginStr = "plugin"

  private val classStr = ".class"

  private def writeDeep(d: VirtualDirectory, path: List[String], suffix: String): OutputStream =
    (path: @unchecked) match {
      case head :: Nil => d.fileNamed(path.head + suffix).output
      case head :: rest =>
        writeDeep(
          d.subdirectoryNamed(head).asInstanceOf[VirtualDirectory],
          rest,
          suffix
        )
    }

  private def enumerateVdFiles(d: VirtualDirectory): Iterator[AbstractFile] = {
    val (subs, files) = d.iterator.partition(_.isDirectory)
    files ++ subs.map(_.asInstanceOf[VirtualDirectory]).flatMap(enumerateVdFiles)
  }

  /**
    * Converts a bunch of bytes into Scalac's weird VirtualFile class
    */
  def makeFile(src: Array[Byte], name: String = "Main.sc"): VirtualFile = {
    val singleFile = new VirtualFile(name)
    val output = singleFile.output
    output.write(src)
    output.close()
    singleFile
  }

  /**
    * Converts Scalac's weird Future type
    * into a standard scala.concurrent.Future
    */
  def awaitResponse[T](func: Response[T] => Unit): T = {
    val r = new Response[T]
    func(r)
    r.get.fold(
      x => x,
      e => throw e
    )
  }

  /**
    * Code to initialize random bits and pieces that are needed
    * for the Scala compiler to function, common between the
    * normal and presentation compiler
    */
  def initGlobalBits(classpath: Seq[java.io.File],
                     dynamicClasspath: VirtualDirectory,
                     settings: Settings): (VirtualDirectory, JavaClassPath) = {
    val vd = new VirtualDirectory("(memory)", None)
    val jCtx = new JavaContext()
    val (dirDeps, jarDeps) = classpath.partition(_.isDirectory)

    def canBeOpenedAsJar(file: File): Boolean = (Try((new ZipFile(file)).close())).isSuccess

    val jarCP =
      jarDeps
        .filter(x => x.getName.endsWith(".jar") || canBeOpenedAsJar(x))
        .map(x => new DirectoryClassPath(new FileZipArchive(x), jCtx))
        .toVector

    val dirCP =
      dirDeps.map(x => new DirectoryClassPath(new PlainDirectory(new Directory(x)), jCtx))

    val dynamicCP = Seq(new DirectoryClassPath(dynamicClasspath, jCtx))
    val jcp = new JavaClassPath(jarCP ++ dirCP ++ dynamicCP, jCtx)

    settings.outputDirs.setSingleOutput(vd)

    settings.nowarnings.value = true
    (vd, jcp)
  }

}
