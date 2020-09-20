package ammonite.kernel

import ammonite.kernel.kernel.{generatedMain, newLine}
import scala.collection.mutable
import scala.tools.nsc.{Global => G}

private[kernel] final case class MungedOutput(code: String, prefixCharLength: Int)

/** Munges input statements into a form that can be fed into scalac
  */
private[kernel] object Munger {

  private case class Transform(code: String, resIden: Option[String])

  private type DCT = (String, String, G#Tree) => Option[Transform]

  def apply(
      stmts: Seq[String],
      resultIndex: String,
      pkgName: Seq[Name],
      indexedWrapperName: Name,
      imports: Imports,
      parse: => String => Either[Seq[LogError], Seq[G#Tree]]): Either[Seq[LogError], MungedOutput] = {

    // type signatures are added below for documentation

    val decls: List[DCT] = {

      def defProc(cond: PartialFunction[G#Tree, G#Name]): DCT =
        (code: String, _: String, tree: G#Tree) =>
          cond.lift(tree).map { _ =>
            Transform(code, None)
        }

      def processor(cond: PartialFunction[(String, String, G#Tree), Transform]): DCT = {
        (code: String, name: String, tree: G#Tree) =>
          cond.lift((name, code, tree))
      }

      val objectDef = defProc {
        case m: G#ModuleDef => m.name
      }

      val classDef = defProc {
        case m: G#ClassDef if !m.mods.isTrait => m.name
      }

      val traitDef = defProc {
        case m: G#ClassDef if m.mods.isTrait => m.name
      }

      val defDef = defProc {
        case m: G#DefDef => m.name
      }

      val typeDef = defProc {
        case m: G#TypeDef => m.name
      }

      val patVarDef = processor {
        case (_, code, _: G#ValDef) => Transform(code, None)
      }

      val importDef = processor {
        case (_, code, _: G#Import) => Transform(code, None)
      }

      val expr = processor {
        //Expressions are lifted to anon function applications so they will be JITed
        case (name, code, _) =>
          Transform(s"private val $name = $code", Some(name))
      }

      List(
        objectDef,
        classDef,
        traitDef,
        defDef,
        typeDef,
        patVarDef,
        importDef,
        expr
      )
    }

    def traverseU[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
      s.foldRight(Right(Nil): Either[A, List[B]]) { (e, acc) =>
        for (xs <- acc.right; x <- e.right) yield x :: xs
      }

    val parsed: Either[Seq[LogError], Seq[(Seq[G#Tree], String)]] = {
      val errorsOrTuples = stmts.map(s => parse(s).map(t => (t, s)))
      val value = traverseU(errorsOrTuples)
      value
    }

    def declParser(inp: ((Seq[G#Tree], String), Int)): Either[Seq[LogError], Transform] =
      inp match {
        case ((trees, code), i) =>
          def handleTree(t: G#Tree): Either[Seq[LogError], Transform] = {
            val parsedDecls: List[Transform] = decls flatMap (x => x(code, "res" + resultIndex + "_" + i, t))
            parsedDecls match {
              case h :: _ => Right(h)
              case Nil =>
                Left(Seq(LogError(s"Dont know how to handle $code")))
            }
          }
          trees match {
            case Seq(h) => handleTree(h)
            case _ if trees.nonEmpty && trees.forall(_.isInstanceOf[G#Import]) =>
              handleTree(trees.head)
            case _ =>
              val filteredSeq = trees filter (_.isInstanceOf[G#ValDef])
              traverseU(filteredSeq.toList.map(handleTree)).map { transforms =>
                transforms.lastOption match {
                  case Some(Transform(_, resIden)) => Transform(code, resIden)
                  case None => Transform(code, None)
                }
              }
          }
      }

    val declTraversed: Either[Seq[LogError], Seq[Transform]] =
      parsed.map(_.zipWithIndex).flatMap(t => traverseU(t.map(declParser)))

    val expandedCode: Either[Seq[LogError], Transform] = declTraversed.map { decls =>
      decls.foldLeft(Transform("", None)) { (acc, t) =>
        Transform(acc.code ++ t.code, t.resIden)
      }
    }

    expandedCode map {
      case Transform(code, resIden) =>
        // can't use strip Margin below because holier-than-thou libraries like shapeless and scalaz use weird
        // characters for identifiers

        val topWrapper = s"""
           package ${pkgName.map(_.backticked).mkString(".")}
           ${importBlock(imports)}
           object ${indexedWrapperName.backticked}{\n"""

        val previousIden = resIden.getOrElse("()")

        val bottomWrapper = s"""
          def $generatedMain = { $previousIden }
          }"""

        val importsLen = topWrapper.length

        MungedOutput(topWrapper + code + bottomWrapper, importsLen)
    }

  }

  def importBlock(importData: Imports): String = {
    // Group the remaining imports into sliding groups according to their
    // prefix, while still maintaining their ordering
    val grouped = mutable.Buffer[mutable.Buffer[ImportData]]()
    for (data <- importData.value) {
      if (grouped.isEmpty) {
        grouped.append(mutable.Buffer(data))
      } else {
        val last = grouped.last.last

        // Start a new import if we're importing from somewhere else, or
        // we're importing the same thing from the same place but aliasing
        // it to a different name, since you can't import the same thing
        // twice in a single import statement
        val startNewImport =
          last.prefix != data.prefix || grouped.last.exists(_.fromName == data.fromName)

        if (startNewImport) {
          grouped.append(mutable.Buffer(data))
        } else {
          grouped.last.append(data)
        }
      }
    }
    // Stringify everything
    val out = for (group <- grouped) yield {
      val printedGroup = for (item <- group) yield {
        if (item.fromName == item.toName) {
          item.fromName.backticked
        } else {
          s"${item.fromName.backticked} => ${item.toName.backticked}"
        }
      }
      val pkgString = group.head.prefix.map(_.backticked).mkString(".")
      "import " + pkgString + s".{$newLine  " +
        printedGroup.mkString(s",$newLine  ") + s"$newLine}$newLine"
    }
    out.mkString
  }

}
