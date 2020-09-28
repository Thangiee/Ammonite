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
          case x =>
            (x.size == 3) && {
              val checks: Seq[String => Boolean] =
                Seq(
                  _.contains("not found: value oogachaka"),
                  _.contains("not found: value life"),
                  _.contains("type mismatch"))
              val zipped = x.zip(checks)
              val (err, fn) = zipped.head
              val tl = zipped.tail
              tl.foldLeft(fn(err.msg)) {
                case (res, (errx, fnx)) => res && (fnx(errx.msg))
              }
            }
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
          case Seq(h, tl @ _*) =>
            tl.isEmpty && ((h.msg.contains("SyntaxError")) || h.msg.contains("'}' expected but eof found"))
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
