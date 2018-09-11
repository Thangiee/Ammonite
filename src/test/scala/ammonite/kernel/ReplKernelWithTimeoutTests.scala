package ammonite.kernel

import concurrent.duration._
import org.scalatest.FlatSpec

abstract class BaseReplKernelWithTimeoutTests(timeout: Duration) extends FlatSpec {

  protected def check0[A](arg: MaybeOutput[A]): Boolean

  protected def check1[A](arg: MaybeOutput[A]): Boolean

  it should "pass process timeout tests" in {
    val kernel = ReplKernelWithTimeout(timeout)

    val res0 = kernel.process("42")
    assert(check0(res0))

    val res1 = kernel.process("45")
    assert(check1(res1))
  }

  it should "pass processBlock timeout tests" in {
    val kernel = ReplKernelWithTimeout(timeout)

    val res0 = kernel.processBlock("42")
    assert(check0(res0))

    val res1 = kernel.processBlock("45")
    assert(check1(res1))
  }

  it should "pass autocomplete timeout tests" in {
    val kernel = ReplKernelWithTimeout(timeout)
    val str = "scala.con<caret>"

    val res0 = kernel.complete(str, str.indexOf("<caret>"))
    assert(check0(res0))

    val res1 = kernel.complete(str, str.indexOf("<caret>"))
    assert(check1(res1))
  }

  it should "pass loadIvy timeout tests" in {
    val kernel = ReplKernelWithTimeout(timeout)

    val res0 = kernel.loadIvy("com.google.guava", "guava", "18.0")
    assert(check0(res0))

    val res1 = kernel.loadIvy("org.apache.commons", "commons-math3", "3.2")
    assert(check1(res1))

  }
}

final class ReplKernelWithTimeoutTimeoutTests extends BaseReplKernelWithTimeoutTests(1.microsecond) {
  override protected final def check0[A](arg: MaybeOutput[A]): Boolean =
    arg match {
      case FailedOutputTimeout => true
      case _ => false
    }

  override protected final def check1[A](arg: MaybeOutput[A]): Boolean =
    arg match {
      case DeadKernel => true
      case _ => false
    }
}

final class ReplKernelWithTimeoutSuccessTests extends BaseReplKernelWithTimeoutTests(1.hour) {
  override protected final def check0[A](arg: MaybeOutput[A]): Boolean =
    arg match {
      case SuccessfulOutput(_) => true
      case _ => false
    }

  override protected final def check1[A](arg: MaybeOutput[A]): Boolean =
    arg match {
      case SuccessfulOutput(_) => true
      case _ => false
    }
}
