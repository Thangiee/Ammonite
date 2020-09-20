package ammonite.kernel

import ammonite.kernel.compat._
import java.util.{List => JList}
import org.scalatest.Assertions._
import scala.collection.JavaConverters.asScalaBufferConverter

object KernelTests {

  type KernelOutput = Either[Seq[LogError], SuccessfulEvaluation]

  type Kernel = (ReplKernel, ReplKernelCompat, ReplKernel)

  def buildKernel(): Kernel =
    (ReplKernel(), new ReplKernelCompat(), ReplKernel())

  def jList2List[T](inp: JList[T]): List[T] =
    asScalaBufferConverter(inp).asScala.toList

  def buildProcessProcessor(inp: KernelOutput => Boolean): KernelProcessProcessor[Any, Boolean] =
    new KernelProcessProcessor[Any, Boolean] {

      override def processEmpty(data: Any): Boolean = inp(Left(Seq.empty))

      override def processError(firstError: String, otherErrors: JList[String], data: Any): Boolean = {
        val nel = LogError(firstError) :: jList2List(otherErrors).map(LogError)
        inp(Left(nel))
      }

      override def processSuccess(value: Any, infos: JList[String], warnings: JList[String], data: Any): Boolean = {
        val res =
          SuccessfulEvaluation(value, jList2List(infos) map LogInfo, jList2List(warnings).map(LogWarning))
        inp(Right(res))
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

  def check(kernel: Kernel, checks: Vector[(String, KernelOutput => Boolean)], isBlock: Boolean) = {
    val (res, idx) = checks.zipWithIndex.foldLeft((true, -1)) {
      case ((res, resIdx), ((code, opTest), idx)) => {
        if (res) {
          val currRes = opTest(kernel._1.process(code)) &&
            kernel._2.process(code, Unit, buildProcessProcessor(opTest)) && (!isBlock || opTest(
            kernel._3.processBlock(code)))
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

  def checkSuccess(kernel: Kernel, checks: Vector[(String, Any => Boolean)], isBlock: Boolean = false) = {
    val modifiedChecks: Vector[(String, KernelOutput => Boolean)] = checks map {
      case (code, fn) =>
        val modified: KernelOutput => Boolean = {
          case Right(SuccessfulEvaluation(x, _, _)) => fn(x)
          case _ => false
        }
        (code, modified)
    }
    check(kernel, modifiedChecks, isBlock)
  }

  def checkFailure(
      kernel: Kernel,
      checks: Vector[(String, PartialFunction[Seq[LogError], Boolean])],
      isBlock: Boolean = false) = {
    val modifiedChecks: Vector[(String, KernelOutput => Boolean)] = checks map {
      case (code, fn) =>
        val modified: KernelOutput => Boolean = {
          case Left(errors) => fn(errors)
          case _ => false
        }
        (code, modified)
    }
    check(kernel, modifiedChecks, isBlock)
  }

  def checkEmpty(kernel: Kernel, strings: Vector[String]) = {
    val checker: KernelOutput => Boolean = {
      case Left(xs) if xs.isEmpty => true
      case _ => false
    }
    val modified = strings map (x => (x, checker))
    check(kernel, modified, false)
  }

}
