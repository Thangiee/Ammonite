package ammonite.kernel

import java.io.{StringWriter, PrintWriter}

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

final case class LogError (override val msg: String) extends LogMessage
final case class LogWarning(override val msg: String) extends LogMessage
final case class LogInfo(override val msg: String) extends LogMessage