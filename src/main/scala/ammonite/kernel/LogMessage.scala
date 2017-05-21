package ammonite.kernel

import java.io.{PrintWriter, StringWriter}

/** Marker trait for messages generated during processing
  *
  * @author Harshad Deo
  * @since 0.1
  */
sealed trait LogMessage extends Serializable {
  val msg: String
}

private[kernel] object LogMessage {
  def fromThrowable(t: Throwable): LogError = {
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    val msg = sw.toString();
    sw.close()
    pw.close()
    LogError(msg)
  }
}

/** Marker type for errors generated during procesing
  *
  * @param msg String representation of the error
  *
  * @author Harshad Deo
  * @since 0.1
  */
final case class LogError(override val msg: String) extends LogMessage

/** Marker type for warnings generated during processing
  *
  * @param msg String representation of the warning
  *
  * @author Harshad Deo
  * @since 0.1
  */
final case class LogWarning(override val msg: String) extends LogMessage

/** Marker type for infos generated during processing
  *
  * @param msg String representation of the info log
  *
  * @author Harshad Deo
  * @since 0.1
  */
final case class LogInfo(override val msg: String) extends LogMessage
