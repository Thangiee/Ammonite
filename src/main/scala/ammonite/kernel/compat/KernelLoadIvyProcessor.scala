package ammonite.kernel.compat

import java.util.{List => JList}

/** Interface to handle the process the output of loading a dependency. Allows the caller to pass in
  * additional data that might be used to construct the output
  *
  * @tparam D Type of the additional data passed in
  * @tparam R Type of the result
  *
  * @author Harshad Deo
  * @since 0.2
  */
trait KernelLoadIvyProcessor[D, R] {

  /** Invoked when loading a dependency results in one or more errors
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def processError(firstError: String, otherErrors: JList[String], data: D): R

  /** Invoked when a dependency is successfuly loaded
    *
    * @author Harshad Deo
    * @since 0.2
    */
  def processSuccess(data: D): R

}
