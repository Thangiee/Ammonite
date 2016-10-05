package ammonite.kernel

/** Output of an autocomplete request
  *
  * {{{
  * val k = ammonite.kernel.ReplKernel()
  * val testStr = "val x = 1; x + x.>"
  * val AutocompleteOutput(names, signatures) = k.complete(testStr, testStr.length)
  * assert(names == Seq(">=", ">>", ">>>"))
  * assert(signatures.contains("def >(x: Byte): Boolean"))
  * }}}
  *
  *
  * @param names names that match at the given locations
  * @param signatures signatures that match at the given location
  *
  * @author Harshad Deo
  * @since 0.1.2
  */
final case class AutocompleteOutput(names: Seq[String], signatures: Seq[String])
