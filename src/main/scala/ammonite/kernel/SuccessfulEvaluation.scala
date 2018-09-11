package ammonite.kernel

/** Result of a successful evaluation
  *
  * @param value output of the last expression
  * @param infos infos generated during processing
  * @param warnings generated during processing
  *
  * @author Harshad Deo
  * @since 0.1.2
  */
final case class SuccessfulEvaluation(value: Any, infos: List[LogInfo], warnings: List[LogWarning])
