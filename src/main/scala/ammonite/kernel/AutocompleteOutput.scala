package ammonite.kernel

final class AutocompleteOutput(val names: Seq[String], val signatures: Seq[String])

object AutocompleteOutput{

  def unapply(op: AutocompleteOutput): Option[(Seq[String], Seq[String])] = Some((op.names, op.signatures))

}