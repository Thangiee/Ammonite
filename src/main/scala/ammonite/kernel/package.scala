package ammonite.kernel

import scala.concurrent.{Future, Promise}
import scalaz.concurrent.Task

package object kernel {

  private[ammonite] type ClassFiles = Vector[(String, Array[Byte])]

  private[ammonite] type SuccessfulCompilation =
    (Seq[LogInfo], List[LogWarning], ClassFiles, Imports)

  private[ammonite] type CompilerOutput =
    Either[Seq[LogError], SuccessfulCompilation]

  private[ammonite] val generatedMain = "$main"

  private[ammonite] val newLine = System.lineSeparator()

  private[ammonite] val rootStr = "_root_"

  implicit final class TaskExtensionOps[A](x: => Task[A]) {
    import scalaz.{-\/, \/-}
    val p: Promise[A] = Promise()
    def runFuture(): Future[A] = {
      x.unsafePerformAsync {
        case -\/(ex) =>
          p.failure(ex); ()
        case \/-(r) => p.success(r); ()
      }
      p.future
    }
  }
}
