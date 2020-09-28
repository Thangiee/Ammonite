package ammonite.kernel

package object kernel {

  private[ammonite] type ClassFiles = Vector[(String, Array[Byte])]

  private[ammonite] type SuccessfulCompilation =
    (Seq[LogInfo], List[LogWarning], ClassFiles, Imports)

  private[ammonite] type CompilerOutput =
    Either[Seq[LogError], SuccessfulCompilation]

  private[ammonite] val generatedMain = "$main"

  private[ammonite] val newLine = System.lineSeparator()

  private[ammonite] val rootStr = "_root_"
}
