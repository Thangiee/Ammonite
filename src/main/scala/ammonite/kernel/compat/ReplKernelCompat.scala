package ammonite.kernel.compat

import ammonite.kernel.{ReplKernel, SuccessfulEvaluation}
import java.util.{List => JList}
import scala.collection.JavaConverters.bufferAsJavaListConverter
import scala.language.implicitConversions
import scala.tools.nsc.Settings

/** Wrapper that removes scala-specific features of [[ReplKernel]], making interop easier
  *
  * @author Harshad Dep
  * @since 0.2
  */
final class ReplKernelCompat private[this] (settings: Settings) {

  private[this] val instance = ReplKernel(settings)

  implicit private def seq2JList[T](seq: Seq[T]): JList[T] =
    bufferAsJavaListConverter(seq.toBuffer).asJava

  /** Construct instance with default settings and repositories
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def this() {
    this(ReplKernel.defaultSettings)
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
      case Left(h +: t) => processor.processError(h.msg, t.map(_.msg), data)
      case Left(_) => processor.processEmpty(data)
      case Right(SuccessfulEvaluation(value, infos, warnings)) =>
        processor.processSuccess(value, infos.map(_.msg), warnings.map(_.msg), data)
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

}
