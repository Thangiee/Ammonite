package ammonite.kernel

import compat._
import java.util.{List => JList}
import org.scalatest.FreeSpec
import KernelTests._

class FailureTests extends FreeSpec {

  val kernel = buildKernel()

  val processor = new KernelLoadIvyProcessor[Any, Boolean] {
    override def processError(firstError: String, otherErrors: JList[String], data: Any) = true
    override def processSuccess(data: Any) = false
  }

  "compileFailure" in {
    checkFailure(
      kernel,
      Vector(
        // ("java", {
        //   case NonEmptyList(h, tl) =>
        //     tl.isEmpty && h.msg.contains(ErrorStrings.JavaCompileFailure)
        // }),
        ("1 + vale", {
          case Seq(err) => err.msg.contains("not found: value vale")
        }),
        ("1 + oogachaka; life; math.sqrt(true)", {
          case Seq(res1, res2, res3) =>
            res1.msg.contains("not found: value oogachaka") &&
              res2.msg.contains("not found: value life") &&
              res3.msg.contains("type mismatch")
        })
      ),
      true
    )
  }

  "compilerCrash" in {
    check(
      kernel,
      Vector(
        ("val x = 1", {
          case Right(SuccessfulEvaluation(x, _, _)) =>
            x match {
              case _: Unit => true
              case _ => false
            }
          case _ => false
        }),
        // wont fail for 2.12.*
        // ("trait Bar { super[Object].hashCode}", {
        //   case Some(Failure(NonEmptyList(h, tl))) if tl.isEmpty =>
        //     h.msg.contains("java.lang.AssertionError: assertion failed")
        //   case _ => false
        // }),
        ("1 + x", {
          case Right(SuccessfulEvaluation(x, _, _)) =>
            x match {
              case y: Int => y == 2
              case _ => false
            }
          case _ => false
        })
      ),
      true
    )
  }

  // "ivyFail" in {
  //   check.session("""
  //       @ import $ivy.`com.lihaoyi::upickle:0.1.12312-DOESNT-EXIST`
  //       error: failed to resolve ivy dependencies
  //     """)
  // }

  "exceptionHandling" in {
    checkFailure(
      kernel,
      Vector(
        ("""throw new Exception("lol", new Exception("hoho"))""", {
          case Seq(h, tl @ _*) =>
            tl.isEmpty && (h.msg.contains("java.lang.Exception: lol")) && (h.msg
              .contains("java.lang.Exception: hoho"))
        })
      ),
      true
    )
  }

  "parseFailure" in {
    checkFailure(
      kernel,
      Vector(
        ("def foo{ ", {
          case xs => xs.nonEmpty
        })
      ),
      true
    )
  }

//  "importFailure" in {
//    checkImportFailure("com.simianquant", "typequux_2.11", "0.1.5")
//    checkImportFailure("com.simianquant", "typequux_2.09", "0.1")
//    checkImportFailure("com.oogachaka", "hoohhaa", "0.23")
//    checkImportFailure("", "", "")
//  }

}
