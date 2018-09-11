package ammonite.kernel.compat

import java.util.{List => JList}

/** Javafriendly wrapper around [[ammonite.kernel.AutocompleteOutput]]
  *
  * @author Harshad Deo
  * @since 0.2
  */
final class AutocompleteOutputCompat(val names: JList[String], val signatures: JList[String])
