package ammonite.kernel

import kernel.rootStr
import reflect.internal.util.{BatchSourceFile, OffsetPosition}
import reflect.NameTransformer
import tools.nsc.plugins.{Plugin, PluginComponent}
import tools.nsc.{Global, Phase}

/**
  * Used to capture the names in scope after every execution, reporting them
  * to the `output` function. Needs to be a compiler plugin so we can hook in
  * immediately after the `typer`
  */
private[kernel] final class AmmonitePlugin(
    override val global: Global,
    output: Seq[ImportData] => Unit,
    topWrapperLen: => Int)
    extends Plugin {

  val name: String = "AmmonitePlugin"

  val description: String =
    "Extracts the names in scope for the Ammonite REPL to use"

  val components: List[PluginComponent] = List(
    new PluginComponent {

      override val global = AmmonitePlugin.this.global

      override val runsAfter = List("typer")

      override val runsBefore = List("patmat")

      override val phaseName = "AmmonitePhase"

      override def newPhase(prev: Phase): Phase = new global.GlobalPhase(prev) {

        override def name: String = phaseName

        override def apply(unit: global.CompilationUnit): Unit = {
          AmmonitePlugin(global)(unit, output)
        }
      }
    },
    new PluginComponent {

      override val global = AmmonitePlugin.this.global

      override val runsAfter = List("parser")

      override val runsBefore = List("namer")

      override val phaseName = "FixLineNumbers"

      override def newPhase(prev: Phase): Phase = new global.GlobalPhase(prev) {

        override def name: String = phaseName

        override def apply(unit: global.CompilationUnit): Unit = {
          LineNumberModifier(global)(unit, topWrapperLen)
        }
      }
    }
  )
}

private[kernel] object AmmonitePlugin {

  var count: Int = 0

  def apply(g: Global)(unit: g.CompilationUnit, output: Seq[ImportData] => Unit): Unit = {

    count += 1
    def decode(t: g.Tree) = {
      val sym = t.symbol
      (sym.isType, sym.decodedName, sym.decodedName, Seq())
    }
    val ignoredSyms = Set(
      "package class-use",
      "object package-info",
      "class package-info"
    )
    val ignoredNames = Set(
      // Probably synthetic
      "<init>",
      "<clinit>",
      "$main",
      // Don't care about this
      "toString",
      // Behaves weird in 2.10.x, better to just ignore.
      "_"
    )
    def saneSym(sym: g.Symbol): Boolean = {
      !sym.name.decoded.contains('$') &&
      sym.exists &&
      !sym.isSynthetic &&
      !sym.isPrivate &&
      !sym.isProtected &&
      sym.isPublic &&
      !ignoredSyms(sym.toString) &&
      !ignoredNames(sym.name.decoded)
    }

    val stats = unit.body.children.last.asInstanceOf[g.ModuleDef].impl.body
    val symbols = stats
      .filter(x => !Option(x.symbol).exists(_.isPrivate))
      .foldLeft(List.empty[(Boolean, String, String, Seq[Name])]) {
        // These are all the ways we want to import names from previous
        // executions into the current one. Most are straightforward, except
        // `import` statements for which we make use of the typechecker to
        // resolve the imported names
        case (ctx, t @ g.Import(expr, selectors)) =>
          def rec(expr: g.Tree): List[(g.Name, g.Symbol)] = {
            expr match {
              case s @ g.Select(lhs, name) => (name -> s.symbol) :: rec(lhs)
              case i @ g.Ident(name) => List(name -> i.symbol)
              case t @ g.This(pkg) => List(pkg -> t.symbol)
            }
          }
          val (nameList, symbolList) = rec(expr).reverse.unzip

          // Note: we need to take the symbol on the left-most name and get it's
          // `.fullName`. Otherwise if we're in
          //
          // ```
          // package foo.bar.baz
          // object Wrapper{val x = ...; import x._}
          // ```
          //
          // The import will get treated as from `Wrapper.x`, but the person
          // running that import will not be in package `foo.bar.baz` and will
          // not be able to find `Wrapper`! Thus we need to get the full name.
          // In cases where the left-most name is a top-level package,
          // `.fullName` is basically a no-op and it works as intended.
          //
          // Apart from this, all other imports should resolve either to one
          // of these cases or importing-from-an-existing import, both of which
          // should work without modification

          val headFullPath = NameTransformer
            .decode(symbolList.head.fullName)
            .split('.')
            .map(Name(_))
          // prefix package imports with `_root_` to try and stop random
          // variables from interfering with them. If someone defines a value
          // called `_root_`, this will still break, but that's their problem
          val rootPrefix = symbolList match {
            case h :: _ if h.hasPackageFlag => List(Name(rootStr))
            case _ => Nil
          }
          val tailPath = nameList.tail.map(x => Name(x.decoded))

          val prefix = rootPrefix ++ headFullPath ++ tailPath

          // A map of each name importable from `expr`, to a `Seq[Boolean]`
          // containing a `true` if there's a type-symbol you can import, `false`
          // if there's a non-type symbol and both if there are both type and
          // non-type symbols that are importable for that name
          val importableIsTypes =
            expr.tpe.members
              .filter(saneSym(_))
              .groupBy(_.name.decoded)
              .mapValues(_.map(_.isType).toVector)

          val renamings = for {
            g.ImportSelector(name, _, rename, _) <- selectors
            isType <- importableIsTypes.getOrElse(name.decode, Nil) // getOrElse just in case...
          } yield Option(rename).map(x => name.decoded -> ((isType, x.decoded)))

          val renameMap = renamings.flatten.map(_.swap).toMap
          val info = new g.analyzer.ImportInfo(t, 0)

          val symNames = for {
            sym <- info.allImportedSymbols
            if saneSym(sym)
          } yield {
            (sym.isType, sym.decodedName)
          }

          val syms = for {
            // For some reason `info.allImportedSymbols` does not show imported
            // type aliases when they are imported directly e.g.
            //
            // import scala.reflect.macros.Context
            //
            // As opposed to via import scala.reflect.macros._.
            // Thus we need to combine allImportedSymbols with the renameMap
            (isType, sym) <- (symNames.toList ++ renameMap.keys).distinct
          } yield {
            (isType, renameMap.getOrElse((isType, sym), sym), sym, prefix)
          }
          syms ::: ctx
        case (ctx, t @ g.DefDef(_, _, _, _, _, _)) => decode(t) :: ctx
        case (ctx, t @ g.ValDef(_, _, _, _)) => decode(t) :: ctx
        case (ctx, t @ g.ClassDef(_, _, _, _)) => decode(t) :: ctx
        case (ctx, t @ g.ModuleDef(_, _, _)) => decode(t) :: ctx
        case (ctx, t @ g.TypeDef(_, _, _, _)) => decode(t) :: ctx
        case (ctx, _) => ctx
      }

    val grouped =
      symbols.distinct
        .groupBy { case (_, b, c, d) => (b, c, d) }
        .mapValues(_.map(_._1))

    val open = for {
      ((fromName, toName, importString), items) <- grouped
      if !ignoredNames(fromName)
    } yield {
      val importType = items match {
        case Seq(true) => ImportData.Type
        case Seq(false) => ImportData.Term
        case Seq(_, _) => ImportData.TermType
      }

      ImportData(Name(fromName), Name(toName), importString, importType)
    }

    // Send the recorded imports through a callback back to the Ammonite REPL.
    // Make sure we sort the imports according to their prefix, so that when
    // they later get rendered the same-prefix imports can be collapsed
    // together v.s. having them by sent in the arbitrary-jumbled order they
    // come out of the `grouped` map in

    output(open.toVector.sortBy(_.prefix.map(_.backticked).mkString(".")))
  }
}

private[kernel] object LineNumberModifier {

  def apply(g: Global)(unit: g.CompilationUnit, topWrapperLen: => Int): Unit = {

    object LineNumberCorrector extends g.Transformer {
      override def transform(tree: g.Tree) = {
        val transformedTree = super.transform(tree)
        tree.pos match {
          case s: scala.reflect.internal.util.OffsetPosition =>
            if (s.point > topWrapperLen) {
              val con = new BatchSourceFile(s.source.file, s.source.content.drop(topWrapperLen))
              val p = new OffsetPosition(con, s.point - topWrapperLen)
              transformedTree.pos = p
            }
          case _ => //for position  = NoPosition
        }
        transformedTree
      }

      def apply(unit: g.CompilationUnit) = transform(unit.body)
    }

    val t = LineNumberCorrector(unit)
    unit.body = t
  }
}
