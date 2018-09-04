package ammonite.kernel.compat

import java.util.{List => JList}

/** Interface to handle the process the output of compiling, loading and evaluating code. Allows the caller to pass in
  * additional data that might be used to construct the output
  *
  * @tparam D Type of the additional data passed in
  * @tparam R Type of the result
  *
  * @author Harshad Deo
  * @since 0.2
  */
trait KernelProcessProcessor[D, R] {

  /** Invoked in the absence of output
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def processEmpty(data: D): R

  /** Invoked if compiling, loading or evaluating the code generates errors. At least one error is guaranteed to be
    * present
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def processError(firstError: String, otherErrors: JList[String], data: D): R

  /** Invoked if processing successfuly results in a value
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def processSuccess(value: Any,
                     infos: JList[String],
                     warnings: JList[String],
                     data: D): R

}
