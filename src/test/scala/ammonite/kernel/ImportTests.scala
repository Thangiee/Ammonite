package ammonite.kernel

import KernelTests._
import org.scalatest.FreeSpec

class ImportTests extends FreeSpec {

  val kernel = buildKernel()

  "basic" - {
    "hello" in {
      checkSuccess(kernel,
                   Vector(
                     ("import math.abs", checkUnit),
                     ("val abs = 123L", checkUnit),
                     ("abs", checkLong(123L))
                   ),
                   true)
    }
    "java" in {
      checkSuccess(
        kernel,
        Vector(
          ("import Thread._", checkUnit),
          ("currentThread.isAlive", checkBoolean(true)),
          ("import java.lang.Runtime.getRuntime", checkUnit),
          ("getRuntime.isInstanceOf[Boolean]", checkBoolean(false)),
          ("getRuntime.isInstanceOf[java.lang.Runtime]", checkBoolean(true))
        ),
        true
      )
    }
    "multi" in {
      checkSuccess(kernel,
                   Vector(
                     ("import math._, Thread._", checkUnit),
                     ("abs(-1)", checkInt(1)),
                     ("currentThread.isAlive", checkBoolean(true))
                   ),
                   true)
    }
    "renaming" in {
      checkSuccess(
        kernel,
        Vector(
          ("import math.{abs => sba}", checkUnit),
          ("sba(-123)", checkInt(123)),
          ("import math.{abs, max => xam}", checkUnit),
          ("abs(-234)", checkInt(234)),
          ("xam(1, 2)", checkInt(2)),
          ("import math.{min => _, _}", checkUnit),
          ("max(2, 3)", checkInt(3))
        ),
        true
      )
    }
  }
  "shadowing" - {
    "sameName" in {
      checkSuccess(
        kernel,
        Vector(
          ("val abs = 'a'", checkUnit),
          ("abs", checkChar('a')),
          ("val abs = 123L", checkUnit),
          ("abs", checkLong(123L)),
          ("import math.abs", checkUnit),
          ("abs(-10)", checkInt(10)),
          ("val abs = 123L", checkUnit),
          ("abs", checkLong(123L)),
          ("import java.lang.Math._", checkUnit),
          ("abs(-4)", checkInt(4))
        ),
        true
      )
    }
    "shadowPrefix" in {
      checkSuccess(
        kernel,
        Vector(
          ("object baz {val foo = 1}", checkUnit),
          ("object foo {val bar = 2}", checkUnit),
          ("import foo.bar", checkUnit),
          ("import baz.foo", checkUnit),
          ("bar", checkInt(2))
        ),
        true
      )
    }

    "typeTermSeparation" - {
      "case1" in {
        checkSuccess(kernel,
                     Vector(("val Foo = 1", checkUnit),
                            ("type Foo = Int", checkUnit),
                            ("Foo", checkInt(1)),
                            ("2: Foo", checkInt(2))),
                     true)
      }

      "case2" in {
        checkSuccess(
          kernel,
          Vector(
            ("""object pkg1{val Order = "lolz"}""", checkUnit),
            ("object pkg2{type Order[+T] = Seq[T]}", checkUnit),
            ("import pkg1._", checkUnit),
            ("Order", checkString("lolz")),
            ("import pkg2._", checkUnit),
            ("Seq(1): Order[Int]", {
              case (h: Int) :: Nil => h == 1
              case _ => false
            }),
            ("Seq(Order): Order[String]", {
              case (h: String) :: Nil => h == "lolz"
              case _ => false
            })
          ),
          true
        )
      }

      "paulp" in {
        checkSuccess(
          kernel,
          Vector(
            ("import ammonite.testcode.paulp1._, ammonite.testcode.paulp2._", checkUnit),
            ("new Paulp; Paulp.toString", checkString("paulp2.Paulp2")),
            ("val Paulp = 123", checkUnit),
            ("new Paulp; Paulp", checkInt(123)),
            ("object Paulp3 {val Paulp = 1; type Paulp = Array[Int]}", checkUnit),
            ("import Paulp3._", checkUnit),
            ("(new Paulp(0)).length", checkInt(0)),
            ("Paulp", checkInt(1)),
            ("""object Paulp4{ object Paulp{override def toString = "Paulp4"}}""", checkUnit),
            ("""object Paulp5{ class Paulp{override def toString = "Paulp5"}}""", checkUnit),
            ("import Paulp4.Paulp, Paulp5.Paulp", checkUnit),
            ("Paulp.toString", checkString("Paulp4")),
            ("(new Paulp).toString", checkString("Paulp5")),
            ("import ammonite.testcode.paulp1._", checkUnit),
            ("(new Paulp).toString", checkString("paulp1.Paulp1")),
            ("Paulp.toString", checkString("Paulp4")),
            ("import ammonite.testcode.paulp2._", checkUnit),
            ("(new Paulp).toString", checkString("paulp1.Paulp1")),
            ("Paulp.toString", checkString("paulp2.Paulp2")),
            ("object Paulp6{ val Paulp = 1; type Paulp = Int }", checkUnit),
            ("import Paulp6._", checkUnit),
            ("Paulp: Paulp", checkInt(1)),
            ("import Paulp4._", checkUnit),
            ("Paulp.toString", checkString("Paulp4")),
            ("val Paulp = 12345", checkUnit),
            ("import Paulp6.Paulp", checkUnit),
            ("Paulp", checkInt(1))
          )
        )
      }
      "paulpTypeRegression" in {
        checkSuccess(
          kernel,
          Vector(
            ("type Paulp = Int", checkUnit),
            ("import ammonite.testcode.paulp3.Paulp", checkUnit),
            ("(new Paulp).toString", checkString("paulp3.Paulp-class"))
          ),
          true
        )
      }
    }

  }
  "collapsing" - {
    checkSuccess(kernel,
                 Vector(
                   ("object Foo{val bar = 1}", checkUnit),
                   ("import Foo.bar", checkUnit),
                   ("import Foo.{bar => _}", checkUnit),
                   ("bar", checkInt(1))
                 ),
                 true)
  }

}
