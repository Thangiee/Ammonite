package ammonite.kernel

import coursier.core.Repository
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.tools.nsc.Settings
import scala.util.{Failure, Success, Try}

/** Encodes the output from [[ReplKernelWithTimeout]]
  *
  * @author Harshad Deo
  * @since 0.4.0
  */
sealed trait MaybeOutput[+A]

/** Output from from [[ReplKernelWithTimeout]] in case the processing request was timed out
  *
  * @author Harshad Deo
  * @since 0.4.0
  */
case object FailedOutputTimeout extends MaybeOutput[Nothing]

/** Output from [[ReplKernelWithTimeout]] in case processing is requested from a kernel instance that was timed out
  *
  * @author Harshad Deo
  * @since 0.4.0
  */
case object DeadKernel extends MaybeOutput[Nothing]

/** Output from [[ReplKernelWithTimeout]] in case processing is requested from a kernel instance that was successfully evaluated
  *
  * @author Harshad Deo
  * @since 0.4.0
  */
case class SuccessfulOutput[A](output: A) extends MaybeOutput[A]

private[kernel] final class ProcessRunnable(
    kernel: ReplKernel,
    code: String,
    promise: Promise[Either[Seq[LogError], SuccessfulEvaluation]],
    isBlock: Boolean)
    extends Runnable {
  override final def run(): Unit = {
    val res = if (isBlock) {
      kernel.processBlock(code)
    } else {
      kernel.process(code)
    }
    promise.success(res)
  }
}

private[kernel] final class ProcessComplete(
    kernel: ReplKernel,
    text: String,
    position: Int,
    promise: Promise[AutocompleteOutput]
) extends Runnable {
  override final def run(): Unit = {
    val res = kernel.complete(text, position)
    promise.success(res)
  }
}

private[kernel] final class ProcessLoadIvy(
    kernel: ReplKernel,
    groupId: String,
    artifactId: String,
    version: String,
    promise: Promise[Seq[LogError]])
    extends Runnable {
  override final def run(): Unit = {
    val res = kernel.loadIvy(groupId, artifactId, version)
    promise.success(res)
  }
}

/** Wrapper around the [[ReplKernel]] that adds a timeout to each processing request. If a request is timed out,
  * subsequent requests cannot be executed. This acts as an effective check against infinite loops.
  *
  * @author Harshad Deo
  * @since 0.4.0
  */
final class ReplKernelWithTimeout(timeout: Duration, settings: Settings, repositories: List[Repository]) {
  private[this] val lock = new AnyRef
  private[this] val kernel = ReplKernel(settings, repositories)
  private[this] var isAlive = true
  implicit private[this] val pool = Executors.newSingleThreadExecutor()

  /** Delegates to the process function of the kernel instance if it is alive
    *
    * @author Harshad Deo
    * @since 0.4.0
    */
  def process(code: String): MaybeOutput[Either[Seq[LogError], SuccessfulEvaluation]] =
    if (isAlive) {
      lock.synchronized {
        val promise = Promise[Either[Seq[LogError], SuccessfulEvaluation]]
        val runnable = new ProcessRunnable(kernel, code, promise, false)
        pool.submit(runnable)
        val result = Try(Await.result(promise.future, timeout))
        result match {
          case Success(op) => SuccessfulOutput(op)
          case Failure(_) =>
            pool.shutdownNow()
            isAlive = false
            FailedOutputTimeout
        }
      }
    } else {
      DeadKernel
    }

  /** Delegates to the processBlock function of the kernel instance if it is alive
    *
    * @author Harshad Deo
    * @since 0.4.0
    */
  def processBlock(code: String): MaybeOutput[Either[Seq[LogError], SuccessfulEvaluation]] =
    if (isAlive) {
      lock.synchronized {
        val promise = Promise[Either[Seq[LogError], SuccessfulEvaluation]]
        val runnable = new ProcessRunnable(kernel, code, promise, true)
        pool.submit(runnable)
        val result = Try(Await.result(promise.future, timeout))
        result match {
          case Success(op) => SuccessfulOutput(op)
          case Failure(_) =>
            isAlive = false
            pool.shutdownNow()
            FailedOutputTimeout
        }
      }
    } else {
      DeadKernel
    }

  /** Delegates to the complete function of the kernel instance if it is alive
    *
    * @author Harshad Deo
    * @since 0.4.0
    */
  def complete(text: String, position: Int): MaybeOutput[AutocompleteOutput] =
    if (isAlive) {
      lock.synchronized {
        val promise = Promise[AutocompleteOutput]()
        val runnable = new ProcessComplete(kernel, text, position, promise)
        pool.submit(runnable)
        val result = Try(Await.result(promise.future, timeout))
        result match {
          case Success(op) => SuccessfulOutput(op)
          case Failure(_) =>
            isAlive = false
            pool.shutdownNow()
            FailedOutputTimeout
        }
      }
    } else {
      DeadKernel
    }

  /** Delegates to the loadIvy function of the kernel instance if it is alive
    *
    * @author Harshad Deo
    * @since 0.4.0
    */
  def loadIvy(groupId: String, artifactId: String, version: String): MaybeOutput[Seq[LogError]] =
    if (isAlive) {
      lock.synchronized {
        val promise = Promise[Seq[LogError]]()
        val runnable =
          new ProcessLoadIvy(kernel, groupId, artifactId, version, promise)
        pool.submit(runnable)
        val result = Try(Await.result(promise.future, timeout))
        result match {
          case Success(op) => SuccessfulOutput(op)
          case Failure(_) =>
            isAlive = false
            pool.shutdownNow()
            FailedOutputTimeout
        }
      }
    } else {
      DeadKernel
    }

  /** Kills the instance
    *
    * @author Harshad Deo
    * @since 0.4.0
    */
  def kill(): Unit = if (isAlive) {
    lock.synchronized {
      pool.shutdownNow()
      isAlive = false
    }
  }

}

object ReplKernelWithTimeout {

  def apply(
      timeout: Duration,
      settings: Settings = ReplKernel.defaultSettings,
      repositories: List[Repository] = ReplKernel.defaultRepositories): ReplKernelWithTimeout =
    new ReplKernelWithTimeout(timeout, settings, repositories)

}
