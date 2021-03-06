package ammonite.kernel

import scala.reflect.NameTransformer

private[kernel] final case class Name(raw: String) {
  assert(
    NameTransformer.decode(raw) == raw,
    "Name() must be created with un-encoded text"
  )

  assert(raw.charAt(0) != '`', "Cannot create already-backticked identifiers")

  def backticked: String = Name.backtickWrap(raw)
}

private[kernel] object Name {

  val alphaKeywords = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "extends",
    "false",
    "finally",
    "final",
    "finally",
    "forSome",
    "for",
    "if",
    "implicit",
    "import",
    "lazy",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "this",
    "throw",
    "trait",
    "try",
    "true",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
    "_",
    "macro"
  )
  val symbolKeywords = Set(
    ":",
    ";",
    "=>",
    "=",
    "<-",
    "<:",
    "<%",
    ">:",
    "#",
    "@",
    "\u21d2",
    "\u2190"
  )

  /**
    * Custom implementation of ID parsing, instead of using the ScalaParse
    * version. This lets us avoid loading FastParse and ScalaParse entirely if
    * we're running a cached script, which shaves off 200-300ms of startup time.
    */
  def backtickWrap(s: String): String = {
    if (s.isEmpty) {
      "``"
    } else if (s(0) == '`' && s.last == '`') {
      s
    } else {
      val chunks = s.split("_", -1)
      def validOperator(c: Char) = {
        c.getType == Character.MATH_SYMBOL ||
        c.getType == Character.OTHER_SYMBOL ||
        "!#%&*+-/:<=>?@\\^|~".contains(c)
      }
      val validChunks = chunks.zipWithIndex.forall {
        case (chunk, index) =>
          chunk.forall(c => c.isLetter || c.isDigit || c == '$') ||
            (chunk.forall(validOperator) &&
              // operators can only come last
              index == chunks.length - 1 &&
              // but cannot be preceded by only a _
              !(chunks.lift(index - 1).contains("") && index - 1 == 0))
      }

      val firstLetterValid = s(0).isLetter || s(0) == '_' || s(0) == '$' || validOperator(s(0))
      val valid =
        validChunks &&
          firstLetterValid &&
          !alphaKeywords.contains(s) &&
          !symbolKeywords.contains(s)

      if (valid) {
        s
      } else {
        '`' + s + '`'
      }
    }
  }
}
