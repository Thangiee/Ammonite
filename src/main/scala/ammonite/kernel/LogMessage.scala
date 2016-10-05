package ammonite.kernel

import java.io.{StringWriter, PrintWriter}

sealed trait LogMessage extends Serializable {
  
  val msg: String

  def accept[D, R](visitor: LogMessageVisitor[D, R], data: D): R

}

trait LogMessageVisitor[D, R]{

  def visitInfo(data: D, msg: String): R

  def visitWarning(data: D, msg: String): R

  def visitError(data: D, msg: String): R

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

final class LogError (override val msg: String) extends LogMessage{

  override def accept[D, R](visitor: LogMessageVisitor[D, R], data: D): R = visitor.visitError(data, msg)

  override def hashCode: Int = msg.hashCode

  override def toString: String = s"LogError($msg)"

  override def equals(other: Any): Boolean = other match {
    case LogError(msg2) => msg2 == msg
    case _ => false
  }

}

object LogError{

  def apply(msg: String): LogError = new LogError(msg)
  
  def unapply(err: LogError): Option[String] = Some(err.msg)

}

final class LogWarning(override val msg: String) extends LogMessage{

  override def accept[D, R](visitor: LogMessageVisitor[D, R], data: D): R = visitor.visitWarning(data, msg)

  override def hashCode: Int = msg.hashCode

  override def toString = s"LogWarning($msg)"

  override def equals(other: Any): Boolean = other match {
    case LogWarning(msg2) => msg == msg2
    case _ => false
  }

}

object LogWarning{

  def apply(msg: String): LogWarning = new LogWarning(msg)

  def unapply(warn: LogWarning): Option[String] = Some(warn.msg)

}

final class LogInfo(override val msg: String) extends LogMessage{

  override def accept[D, R](visitor: LogMessageVisitor[D, R], data: D): R = visitor.visitInfo(data, msg)

  override def hashCode: Int = msg.hashCode

  override def toString: String = s"LogInfo($msg)"

  override def equals(other: Any): Boolean = other match {
    case LogInfo(msg2) => msg == msg2
    case _ => false
  }

}

object LogInfo{

  def apply(msg: String): LogInfo = new LogInfo(msg)

  def unapply(inf: LogInfo): Option[String] = Some(inf.msg)

}
