package ammonite.kernel.compat

import ammonite.kernel.{ReplKernel, SuccessfulEvaluation}
import collection.JavaConverters.{asScalaBufferConverter, bufferAsJavaListConverter}
import coursier.core.Repository
import java.util.{List => JList}
import language.implicitConversions
import scalaz.{Failure, NonEmptyList, Success}
import tools.nsc.Settings

/** Wrapper that removes scala-specific features of [[ReplKernel]], making interop easier
  *
  * @author Harshad Dep
  * @since 0.2
  */
final class ReplKernelCompat private[this] (settings: Settings, repositories: List[Repository]) {

  private[this] val instance = ReplKernel(settings, repositories)

  implicit private def seq2JList[T](seq: Seq[T]): JList[T] =
    bufferAsJavaListConverter(seq.toBuffer).asJava

  /** Construct instance with default settings and repositories
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def this() {
    this(ReplKernel.defaultSettings, ReplKernel.defaultRepositories)
  }

  /** Construct instance with specified settings and default repositories
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def this(settings: Settings) {
    this(settings, ReplKernel.defaultRepositories)
  }

  /** Construct instance with specified repositories and default settings
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def this(repositories: JList[Repository]) {
    this(ReplKernel.defaultSettings, asScalaBufferConverter(repositories).asScala.toList)
  }

  /** Construct instance with specified settings and repositories
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def this(settings: Settings, repositories: JList[Repository]) {
    this(settings, asScalaBufferConverter(repositories).asScala.toList)
  }

  /** Compiles, loads and evaluates the supplied code
    *
    * @param code code to be processed
    * @param data additional data passed to the processor
    * @param processor handles the output of the compiling, loading and evaluating the code
    *
    * @tparam D Type of the additional data passed in
    * @tparam R Type of the result
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def process[D, R](code: String, data: D, processor: KernelProcessProcessor[D, R]): R =
    instance.process(code) match {
      case None => processor.processEmpty(data)
      case Some(Failure(NonEmptyList(h, t))) =>
        val tailJList = t.toList map (_.msg)
        processor.processError(h.msg, tailJList, data)
      case Some(Success(SuccessfulEvaluation(value, infos, warnings))) =>
        processor.processSuccess(value, infos map (_.msg), warnings map (_.msg), data)
    }

  /** Provides semantic autocompletion at the indicated position, in the context of the current classpath and previously
    * evaluated expressions
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def complete(text: String, position: Int): AutocompleteOutputCompat = {
    val res = instance.complete(text, position)
    new AutocompleteOutputCompat(res.names, res.signatures)
  }

  /** Adds a dependency on an external library using maven coordinates
    *
    * @param data additional data passed to the processor
    * @param processor handles the output of loading the dependency
    *
    * @tparam D Type of the additional data passed in
    * @tparam R Type of the result
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def loadIvy[D, R](groupId: String,
                    artifactId: String,
                    version: String,
                    data: D,
                    processor: KernelLoadIvyProcessor[D, R]): R = {
    val res = instance.loadIvy(groupId, artifactId, version)
    res match {
      case Success(_) => processor.processSuccess(data)
      case Failure(NonEmptyList(h, t)) =>
        processor.processError(h.toString, t.toList map (_.msg), data)
    }
  }

  /** Adds a repository that can be subsequently used to load dependencies
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def addRepository(repository: Repository): Unit =
    instance.addRepository(repository)
}
