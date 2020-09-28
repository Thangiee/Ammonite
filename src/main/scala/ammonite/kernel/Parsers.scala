package ammonite.kernel

import fastparse._, ScalaWhitespace._
import scalaparse.Scala._

object Parsers {

  def splitter[_: P]: P[Seq[String]] = P(statementBlocl ~ WL ~ End)

  private def prelude[_: P] = P((Annot ~ OneNLMax).rep ~ (Mod ~/ Pass).rep)

  private def statement[_: P] =
    P(scalaparse.Scala.TopPkgSeq | scalaparse.Scala.Import | prelude ~ BlockDef | StatCtx.Expr)

  private def statementBlocl[_: P] =
    P(Semis.? ~ (statement ~~ WS ~~ (Semis | End)).!.repX)

  // /**
  //   * Attempts to break a code blob into multiple statements. Returns `None` if
  //   * it thinks the code blob is "incomplete" and requires more input
  //   */
  // def split(code: String): Option[fastparse.core.Parsed[Seq[String]]] =
  //   splitter.parse(code) match {
  //     case Failure(_, index, extra) if code.drop(index).trim() == "" => None
  //     case x => Some(x)
  //   }

}
