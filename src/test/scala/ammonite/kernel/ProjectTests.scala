package ammonite.kernel

import ammonite.kernel.KernelTests._
import ammonite.kernel.compat._
import java.util.{List => JList}
import org.scalatest.FreeSpec

class ProjectTests extends FreeSpec {

  val kernel = buildKernel()

  val processor = new KernelLoadIvyProcessor[Any, Boolean] {
    override def processError(firstError: String, otherErrors: JList[String], data: Any) = false
    override def processSuccess(data: Any) = true
  }

  def checkImportSuccess(groupId: String, artifactId: String, version: String): Unit = {
    val rawSuccess = kernel._1.loadIvy(groupId, artifactId, version).isEmpty
    val compatSuccess =
      kernel._2.loadIvy(groupId, artifactId, version, (), processor)
    assert(rawSuccess && compatSuccess)
  }

  "scalatags" in {
    checkFailure(
      kernel,
      Vector(
        ("import scalatags.Text.all._", {
          case Seq(h, tl @ _*) =>
            tl.isEmpty && h.msg.contains("not found: value scalatags")
        })
      ))
    checkImportSuccess("com.lihaoyi", ProjectNames.scalaTags, "0.6.3")
    checkSuccess(
      kernel,
      Vector(
        ("import scalatags.Text.all._", checkUnit),
        ("""a("omg", href:="www.google.com").render""", {
          case s: String => s.contains("""<a href="www.google.com">omg</a>""")
          case _ => false
        })
      )
    )
  }

  "shapeless" in {
    checkImportSuccess("com.chuusai", ProjectNames.shapeless, "2.3.2")
    checkSuccess(
      kernel,
      Vector(
        ("import shapeless._", checkUnit),
        ("""val a = (1 :: "lol" :: List(1, 2, 3) :: HNil)""", checkUnit),
        ("a(0)", checkInt(1)),
        ("a(1)", checkString("lol")),
        ("import shapeless.syntax.singleton._", checkUnit),
        ("2.narrow", checkInt(2))
      )
    )
  }

  "guava" in {
    checkImportSuccess("com.google.guava", "guava", "18.0")
    checkSuccess(
      kernel,
      Vector(
        ("import com.google.common.collect._", checkUnit),
        ("""val bimap = ImmutableBiMap.of(1, "one", 2, "two", 3, "three")""", checkUnit),
        ("bimap.get(1)", checkString("one")),
        ("""bimap.inverse.get("two")""", checkInt(2))
      )
    )
  }

  "spire" in {
    checkImportSuccess("org.spire-math", ProjectNames.spire, "0.13.0")
    checkSuccess(
      kernel,
      Vector(
        ("import spire.implicits._", checkUnit),
        ("import spire.math._", checkUnit),
        (
          """
          def euclidGcd[A: Integral](x: A, y: A): A = {
            if (y == 0) x
            else euclidGcd(y, x % y)
          }
          """,
          checkUnit),
        ("euclidGcd(42, 96)", checkInt(6)),
        ("euclidGcd(42L, 96L)", checkLong(6L)),
        ("def mean[A: Fractional](xs: A*): A = xs.reduceLeft(_ + _) / xs.size", checkUnit),
        ("mean(0.5, 1.5, 0.0, -0.5)", checkDouble(0.375))
      )
    )

  }

}
