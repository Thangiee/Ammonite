package ammonite.kernel

import concurrent.duration._
import org.scalatest.FlatSpec

final class InfiniteLoopTests extends FlatSpec {

  private val timeout = 10.seconds
  private val kernel = ReplKernelWithTimeout(timeout)

  it should "pass initial success" in {
    val arg = """|def foo(x: Int) = x * 100
                 |foo(2)""".stripMargin
    val processed = kernel.process(arg)
    val res = processed match {
      case SuccessfulOutput(_) => true
      case _ => false
    }
    assert(res)
  }

  it should "pass infinite loop failure" in {
    val arg = """|while(true){
                 | Thread.sleep(100)
                 |}""".stripMargin
    val processed = kernel.process(arg)
    val res = processed match {
      case FailedOutputTimeout => true
      case _ => false
    }
    assert(res)
  }

  it should "pass deadkernel process tests" in {
    val processed = kernel.process("42")
    val res = processed match {
      case DeadKernel => true
      case _ => false
    }
    assert(res)
  }

  it should "pass deadkernel processBlock tests" in {
    val processed = kernel.processBlock("42")
    val res = processed match {
      case DeadKernel => true
      case _ => false
    }
    assert(res)
  }

  it should "pass deadkernel autocomplete tests" in {
    val str = "def fo<caret>"
    val processed = kernel.complete(str, str.indexOf("caret"))
    val res = processed match {
      case DeadKernel => true
      case _ => false
    }
    assert(res)
  }

  it should "pass loadIvy tests" in {
    val processed = kernel.loadIvy("com.google.guava", "guava", "18.0")
    val res = processed match {
      case DeadKernel => true
      case _ => false
    }
    assert(res)
  }

}
