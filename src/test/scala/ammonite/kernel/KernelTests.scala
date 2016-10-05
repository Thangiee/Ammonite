package ammonite.kernel

import collection.JavaConversions.asScalaBuffer
import compat._
import java.util.{List => JList}
import scalaz.{Name => _, _}
import org.scalatest.Assertions._

object KernelTests {

  type KernelOutput = Option[ValidationNel[LogError, SuccessfulEvaluation]]

  type Kernel = (ReplKernel, ReplKernelCompat)

  def buildKernel(): Kernel = (ReplKernel(), new ReplKernelCompat())

  def jList2List[T](inp: JList[T]): List[T] = asScalaBuffer(inp).toList

  def buildProcessProcessor(inp: KernelOutput => Boolean): KernelProcessProcessor[Any, Boolean] =
    new KernelProcessProcessor[Any, Boolean] {

      override def processEmpty(data: Any): Boolean = inp(None)

      override def processError(firstError: String, otherErrors: JList[String], data: Any): Boolean = {
        val nel = NonEmptyList(LogError(firstError), jList2List(otherErrors).map(LogError(_)): _*)
        inp(Some(Failure(nel)))
      }

      override def processSuccess(value: Any, infos: JList[String], warnings: JList[String], data: Any): Boolean = {
        val res =
          SuccessfulEvaluation(value, jList2List(infos) map (LogInfo(_)), jList2List(warnings) map (LogWarning(_)))
        inp(Some(Success(res)))
      }
    }

  val checkUnit: Any => Boolean = {
    case _: Unit => true
    case _ => false
  }

  def checkInt(i: Int): Any => Boolean = {
    case x: Int => x == i
    case _ => false
  }

  def checkLong(l: Long): Any => Boolean = {
    case x: Long => x == l
    case _ => false
  }

  def checkString(s: String): Any => Boolean = {
    case x: String => x == s
    case _ => false
  }

  def checkChar(c: Char): Any => Boolean = {
    case x: Char => x == c
    case _ => false
  }

  def checkBoolean(b: Boolean): Any => Boolean = {
    case x: Boolean => x == b
    case _ => false
  }

  def checkDouble(d: Double): Any => Boolean = {
    case x: Double => x == d
    case _ => false
  }

  def check(kernel: Kernel, checks: Vector[(String, KernelOutput => Boolean)]) = {
    val (res, idx) = checks.zipWithIndex.foldLeft((true, -1)) {
      case ((res, resIdx), ((code, opTest), idx)) => {
        if (res) {
          val currRes = opTest(kernel._1.process(code)) && kernel._2.process(code, Unit, buildProcessProcessor(opTest))
          if (currRes) {
            (currRes, -1)
          } else {
            (currRes, idx)
          }
        } else {
          (res, resIdx)
        }
      }
    }
    val msg: Any = if (idx != -1) s"failed for input: ${checks(idx)._1}"
    assert(res, msg)
  }

  def checkSuccess(kernel: Kernel, checks: Vector[(String, Any => Boolean)]) = {
    val modifiedChecks: Vector[(String, KernelOutput => Boolean)] = checks map {
      case (code, fn) =>
        val modified: KernelOutput => Boolean = {
          case Some(Success(SuccessfulEvaluation(x, _, _))) => fn(x)
          case _ => false
        }
        (code, modified)
    }
    check(kernel, modifiedChecks)
  }

  def checkFailure(kernel: Kernel, checks: Vector[(String, NonEmptyList[LogError] => Boolean)]) = {
    val modifiedChecks: Vector[(String, KernelOutput => Boolean)] = checks map {
      case (code, fn) =>
        val modified: KernelOutput => Boolean = {
          case Some(Failure(errors)) => fn(errors)
          case _ => false
        }
        (code, modified)
    }
    check(kernel, modifiedChecks)
  }

  def checkEmpty(kernel: Kernel, strings: Vector[String]) = {
    val checker: KernelOutput => Boolean = {
      case None => true
      case _ => false
    }
    val modified = strings map (x => (x, checker))
    check(kernel, modified)
  }

}
