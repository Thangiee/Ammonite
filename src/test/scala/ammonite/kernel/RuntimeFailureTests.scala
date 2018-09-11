package ammonite.kernel

import org.scalatest.FlatSpec
import scalaz._

final class RuntimeFailureTests extends FlatSpec {

  private val kernel = ReplKernel()

  it should "pass success-0 test" in {
    val op = kernel.process("42")
    val res = op match {
      case Some(Success(SuccessfulEvaluation(x, infos, warns))) => (x == 42) && infos.isEmpty && warns.isEmpty
      case _ => false
    }
    assert(res)
  }

  it should "pass failure-1 test" in {
    val str = """|val a: String = null
                 |val b = a.toUpperCase""".stripMargin
    val op = kernel.process(str)
    val res = op match {
      case Some(Failure(NonEmptyList(first, rest))) if rest.isEmpty =>
        first.msg.contains("java.lang.NullPointerException")
      case _ => false
    }
    assert(res)
  }

  it should "pass success-1 test" in {
    val op = kernel.process(""""foo"""")
    val res = op match {
      case Some(Success(SuccessfulEvaluation(x, infos, warns))) => (x == "foo") && infos.isEmpty && warns.isEmpty
      case _ => false
    }
    assert(res)
  }

  it should "pass failure-2 test" in {
    val str = """|val c: String = null
                 |val d = c.toUpperCase
                 |42""".stripMargin
    val op = kernel.process(str)
    val res = op match {
      case Some(Failure(NonEmptyList(first, rest))) if rest.isEmpty =>
        first.msg.contains("java.lang.NullPointerException")
      case _ => false
    }
    assert(res)

  }

  it should "pass failure-3 test" in {
    val op = kernel.process("???")
    val res = op match {
      case Some(Failure(NonEmptyList(first, rest))) if rest.isEmpty =>
        first.msg.contains("scala.NotImplementedError")
      case _ => false
    }
    assert(res)
  }

  it should "pass success-2 test" in {
    val str = """|val a = List(11, 12, 13)
                 |a(0)""".stripMargin
    val op = kernel.process(str)
    val res = op match {
      case Some(Success(SuccessfulEvaluation(x, infos, warns))) => (x == 11) && infos.isEmpty && warns.isEmpty
      case _ => false
    }
    assert(res)
  }

  it should "pass success-3 test" in {
    val op = kernel.process("a.tail")
    val res = op match {
      case Some(Success(SuccessfulEvaluation(x, infos, warns))) => (x == List(12, 13)) && infos.isEmpty && warns.isEmpty
      case _ => false
    }
    assert(res)
  }

  it should "pass failure-4 test" in {
    val str = """val a: String = null
                |val b = a.toUpperCase
                |val c = b.toLowerCase
                |c""".stripMargin
    val op = kernel.process(str)
    val res = op match {
      case Some(Failure(NonEmptyList(first, rest))) if rest.isEmpty =>
        first.msg.contains("NullPointerException")
      case _ => false
    }
    assert(res)
  }

  it should "pass success-4 test" in {
    val op = kernel.process("a.last")
    val res = op match {
      case Some(Success(SuccessfulEvaluation(x, infos, warns))) => (x == 13) && infos.isEmpty && warns.isEmpty
      case _ => false
    }
    assert(res)
  }

  it should "pass failure-5 test" in {
    kernel.process("val a: String = null")
    val op = kernel.process("a.toUpperCase")
    val res = op match {
      case Some(Failure(NonEmptyList(first, rest))) if rest.isEmpty =>
        first.msg.contains("NullPointerException")
      case _ => false
    }
    assert(res)
  }

}
