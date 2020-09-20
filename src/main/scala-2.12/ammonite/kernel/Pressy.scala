package ammonite.kernel

import java.io.File
import kernel.newLine
import Pressy._
import reflect.internal.util.{BatchSourceFile, OffsetPosition, Position}
import reflect.io.VirtualDirectory
import tools.nsc.interactive.{Global, Response}
import tools.nsc.reporters.AbstractReporter
import tools.nsc.Settings
import tools.nsc.util._
import util.{Failure, Success, Try}

/**
  * Nice wrapper for the presentation compiler.
  */
private[kernel] final class Pressy(nscGen: => Global) {

  private[this] val lock = new AnyRef

  private lazy val nscGlobal = nscGen

  /**
    * Ask for autocompletion at a particular spot in the code, returning
    * possible things that can be completed at that location. May try various
    * different completions depending on where the `index` is placed, but
    * the outside caller probably doesn't care.
    */
  def complete(snippet: String, snippetIndex: Int, previousImports: String): AutocompleteOutput =
    lock.synchronized {
      val prefix = previousImports + newLine + "object AutocompleteWrapper{" + newLine
      val suffix = newLine + "}"
      val allCode = prefix + snippet + suffix
      val index = snippetIndex + prefix.length

      val currentFile = new BatchSourceFile(Compiler.makeFile(allCode.getBytes, name = "Current.sc"), allCode)

      val r = new Response[Unit]
      nscGlobal.askReload(List(currentFile), r)
      r.get.fold(x => x, e => throw e)

      val run = Try(new Run(nscGlobal, currentFile, allCode, index))

      val (_, all): (Int, Seq[(String, Option[String])]) = run match {
        case Success(runSuccess) => runSuccess.prefixed
        case Failure(_) => (0, Seq.empty)
      }

      val allNames = all.collect { case (name, None) => name }.sorted.distinct

      val signatures =
        all.collect { case (_, Some(defn)) => defn }.sorted.distinct

      AutocompleteOutput(allNames, signatures)
    }

  override def finalize(): Unit = {
    nscGlobal.askShutdown()
  }

}

private[kernel] object Pressy {

  private val errorStr = "<error>"
  private val emptyStr = ""

  /**
    * Encapsulates all the logic around a single instance of
    * `nsc.interactive.Global` and other data specific to a single completion
    */
  private class Run(val pressy: Global, currentFile: BatchSourceFile, allCode: String, index: Int) {

    /**
      * Dumb things that turn up in the autocomplete that nobody needs or wants
      */
    private def blacklisted(s: pressy.Symbol) = {
      val blacklist = Set(
        "scala.Predef.any2stringadd.+",
        "scala.Any.##",
        "java.lang.Object.##",
        "scala.<byname>",
        "scala.<empty>",
        "scala.<repeated>",
        "scala.<repeated...>",
        "scala.Predef.StringFormat.formatted",
        "scala.Predef.Ensuring.ensuring",
        "scala.Predef.ArrowAssoc.->",
        "scala.Predef.ArrowAssoc.→",
        "java.lang.Object.synchronized",
        "java.lang.Object.ne",
        "java.lang.Object.eq",
        "java.lang.Object.wait",
        "java.lang.Object.notifyAll",
        "java.lang.Object.notify"
      )

      blacklist(s.fullNameAsName('.').decoded) ||
      s.isImplicit ||
      // Cache objects, which you should probably never need to
      // access directly, and apart from that have annoyingly long names
      "cache[a-f0-9]{32}".r.findPrefixMatchOf(s.name.decoded).isDefined ||
      s.isDeprecated ||
      s.decodedName == "<init>" ||
      s.decodedName.contains('$')
    }
    private val r = new Response[pressy.Tree]
    pressy.askTypeAt(new OffsetPosition(currentFile, index), r)
    private val tree = r.get.fold(x => x, e => throw e)

    /**
      * Search for terms to autocomplete not just from the local scope,
      * but from any packages and package objects accessible from the
      * local scope
      */
    private def deepCompletion(name: String) = {
      def rec(t: pressy.Symbol): Seq[pressy.Symbol] = {
        val children =
          if (t.hasPackageFlag || t.isPackageObject) {
            pressy.ask(() => t.typeSignature.members.filter(_ != t).flatMap(rec))
          } else {
            Nil
          }

        t +: children.toSeq
      }

      for {
        member <- pressy.RootClass.typeSignature.members.toList
        sym <- rec(member)
        // sketchy name munging because I don't know how to do this properly
        strippedName = sym.nameString.stripPrefix("package$").stripSuffix("$")
        if strippedName.startsWith(name)
        (pref, _) = sym.fullNameString.splitAt(sym.fullNameString.lastIndexOf('.') + 1)
        out = pref + strippedName
        if out.nonEmpty
      } yield (out, None)
    }

    private def handleTypeCompletion(position: Int, decoded: String, offset: Int) = {

      val r = ask(position, pressy.askTypeCompletion)
      val prefix = if (decoded == errorStr) emptyStr else decoded
      (position + offset, handleCompletion(r, prefix))
    }

    private def handleCompletion(r: List[pressy.Member], prefix: String) =
      pressy.ask { () =>
        r.filter(_.sym.name.decoded.startsWith(prefix))
          .filter(m => !blacklisted(m.sym))
          .map { x =>
            (
              x.sym.name.decoded,
              if (x.sym.name.decoded != prefix) { None } else {
                Some(x.sym.defString)
              }
            )
          }
      }

    def prefixed: (Int, Seq[(String, Option[String])]) = tree match {
      case t @ pressy.Select(qualifier, name) =>
        val dotOffset = if (qualifier.pos.point == t.pos.point) 0 else 1

        //In scala 2.10.x if we call pos.end on a scala.reflect.internal.util.Position
        //that is not a range, a java.lang.UnsupportedOperationException is thrown.
        //We check here if Position is a range before calling .end on it.
        //This is not needed for scala 2.11.x.
        if (qualifier.pos.isRange) {
          handleTypeCompletion(qualifier.pos.end, name.decoded, dotOffset)
        } else {
          //not prefixed
          (0, Seq.empty)
        }

      case pressy.Import(expr, selectors) =>
        // If the selectors haven't been defined yet...
        if (selectors.head.name.toString == errorStr) {
          if (expr.tpe.toString == errorStr) {
            // If the expr is badly typed, try to scope complete it
            if (expr.isInstanceOf[pressy.Ident]) {
              val exprName = expr.asInstanceOf[pressy.Ident].name.decoded
              expr.pos.point -> handleCompletion(
                ask(expr.pos.point, pressy.askScopeCompletion),
                // if it doesn't have a name at all, accept anything
                if (exprName == errorStr) emptyStr else exprName
              )
            } else {
              (expr.pos.point, Seq.empty)
            }
          } else {
            // If the expr is well typed, type complete
            // the next thing
            handleTypeCompletion(expr.pos.end, emptyStr, 1)
          }
        } else { // I they're been defined, just use typeCompletion
          handleTypeCompletion(selectors.last.namePos, selectors.last.name.decoded, 0)
        }
      case t @ pressy.Ident(name) =>
        lazy val shallow = handleCompletion(
          ask(index, pressy.askScopeCompletion),
          name.decoded
        )
        lazy val deep = deepCompletion(name.decoded).distinct

        if (shallow.nonEmpty) {
          (t.pos.start, shallow)
        } else if (deep.length == 1) {
          (t.pos.start, deep)
        } else {
          (t.pos.end, deep :+ (emptyStr -> None))
        }

      case _ =>
        val comps = ask(index, pressy.askScopeCompletion)

        index -> pressy.ask(() =>
          comps.filter(m => !blacklisted(m.sym)).map { s =>
            (s.sym.name.decoded, None)
        })
    }
    private def ask(index: Int, query: (Position, Response[List[pressy.Member]]) => Unit) = {
      val position = new OffsetPosition(currentFile, index)
      //if a match can't be found awaitResponse throws an Exception.
      val result = Try(Compiler.awaitResponse[List[pressy.Member]](query(position, _)))
      result match {
        case Success(scopes) => scopes.filter(_.accessible)
        case Failure(_) => List.empty[pressy.Member]
      }
    }

  }
  def apply(
      classpath: Seq[File],
      dynamicClasspath: VirtualDirectory,
      settings: Settings,
      evalClassloader: => ClassLoader): Pressy = {

    def initialize = {
      val (_, jcp) = Compiler.initGlobalBits(
        classpath,
        dynamicClasspath,
        settings
      )
      val settingsX = settings
      val reporter = new AbstractReporter {

        override def displayPrompt(): Unit = ()

        override def display(pos: Position, msg: String, severity: Severity) =
          ()

        override val settings = settingsX
      }
      new Global(settings, reporter) { g =>
        // Actually jcp, avoiding a path-dependent type issue in 2.10 here
        override def classPath = jcp

        override lazy val platform: ThisPlatform = new GlobalPlatform {
          override val global = g
          override val settings = g.settings
          override val classPath = jcp
        }

        override lazy val analyzer =
          CompilerCompatibility.interactiveAnalyzer(g, evalClassloader)
      }

      // new Global(settings, reporter) { g =>
      //   // Actually jcp, avoiding a path-dependent type issue in 2.10 here
      //   override def classPath = platform.classPath

      //   override lazy val platform: ThisPlatform = new JavaPlatform {
      //     val global: g.type = g

      //     override def classPath = jcp
      //   }
      //   override lazy val analyzer =
      //     CompilerCompatibility.interactiveAnalyzer(g, evalClassloader)
      // }
    }

    new Pressy(initialize)
  }
}
