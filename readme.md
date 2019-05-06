Ammonite-Kernel
===

A stripped-down fork of [Ammonite](https://github.com/lihaoyi/Ammonite) designed for scala and java applications that need to compile and execute scala code at runtime.

[![Build Status](https://travis-ci.org/SimianQuant/Ammonite.svg?branch=master)](https://travis-ci.org/SimianQuant/Ammonite)
[![Build status](https://ci.appveyor.com/api/projects/status/eldji8y9ot2gguxr?svg=true)](https://ci.appveyor.com/project/harshad-deo/ammonite-ci0rt)
[![Coverage Status](https://coveralls.io/repos/github/SimianQuant/Ammonite/badge.svg?branch=master)](https://coveralls.io/github/SimianQuant/Ammonite?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.simianquant/ammonite-kernel_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.simianquant/ammonite-kernel_2.12)
[![Gitter](https://badges.gitter.im/Ammonite-kernel/amonite-kernel.svg)](https://gitter.im/Ammonite-kernel/ammonite-kernel?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Scaladoc](http://javadoc-badge.appspot.com/com.simianquant/ammonite-kernel_2.12.svg?label=scaladoc)](http://javadoc-badge.appspot.com/com.simianquant/ammonite-kernel_2.12)
[![Gitter](https://badges.gitter.im/SimianQuant/Ammonite.svg)](https://gitter.im/SimianQuant/Ammonite?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

* [Setup](#setup)
  - [SBT](#sbt)
  - [Gradle](#gradle)
* [Need](#need)
* [Usage](#usage)
  - [Scala](#scala)
  - [Java](#java)
* [Changelog](#changelog)

Setup
-----

Add the usual lines to the build file:

#### SBT

```scala
libraryDependencies += "com.simianquant" %% "ammonite-kernel" % "0.4.2"
```

#### Gradle

```groovy
compile 'com.simianquant:ammonite-kernel_2.11:0.4.2'
```
or 
```groovy
compile 'com.simianquant:ammonite-kernel_2.12:0.4.2'
```



Need
----

While Ammonite works well as a stand-alone application, it is not well-suited for embedded usage because:

1. **Separation of Concerns**: The code to compile, parse, load and evaluate a statement is not separate from that to read it from an input source and 
  print the result to an output source. Though it is [possible](https://github.com/lihaoyi/Ammonite/blob/master/amm/src/test/scala/ammonite/TestRepl.scala)
  to feed in strings and assign the printed output to a string, doing so is quite convoluted. Also there is no simple way to obtain
  the value of an evaluated expression.
2. **Thread Safety**: The coupled mutable state of the application is spread across several classes and several methods without proper synchronization, 
	making multi-threaded usage tricky at best.
3. **Static leakage**: At the time of the fork, several classes leaked static state, making it complicated to run several instances of ammonite at once. 

Usage
---

#### Scala

The functionality is encapsulated in `ReplKernel`. The static output types do not add anything to the examples below, and have been ommitted for clarity.

```scala
scala> import ammonite.kernel._ // bringing stuff into scope
import ammonite.kernel._

scala> val kernel = ReplKernel() // to carry out the operations
kernel: /**/ = ammonite.kernel.ReplKernel@6e6a20a8

scala> val kernelBkp = ReplKernel() // to illustrate the absence of static leakage
kernelBkp: /**/ = ammonite.kernel.ReplKernel@4189b9c5

scala> kernel.process("")
res0: /**/ = None

scala> kernel.process("oogachaka") // name error
res1: /**/ =
Some(Failure(NonEmpty[LogError(_ReplKernel0.sc:1: not found: value oogachaka
private val res0_0 = oogachaka
                     ^)]))

scala> kernel.process("def foo{") // syntax error
res2: /**/ =
Some(Failure(NonEmpty[LogError(SyntaxError: found "", expected ";" | Newline.rep(1) | "}" | `case` at index 8
def foo{
        ^)]))

scala> kernel.process("""def greet(name: String) = s"Hello, $name " """)
res3: /**/ = Some(Success(SuccessfulEvaluation((),List(),List())))

scala> kernel.process("""greet("Harshad")""") // use the function
res4: /**/ = Some(Success(SuccessfulEvaluation(Hello, Harshad ,List(),List())))

scala> kernelBkp.process("""greet("Harshad")""") // different instance, does not work 
res5: /**/ =
Some(Failure(NonEmpty[LogError(_ReplKernel0.sc:1: not found: value greet
private val res0_0 = greet("Harshad")
                     ^)]))

scala> val testStr1 = "import collection.immutable.Li"; val testStr2 = "gre"
testStr1: String = import collection.immutable.Li
testStr2: String = gre

scala> kernel.complete(testStr1, testStr1.length)
res6: /**/ = AutocompleteOutput(List(LinearSeq, List, ListMap, ListSerializeEnd, ListSet),List())

scala> kernel.complete(testStr2, testStr2.length) // autocomplete includes names defined earlier
res7: /**/ = AutocompleteOutput(List(greet),List())

scala> kernel.process("import ammonite.kernel.testcode.Newton") // importing classpath objects
res8: /**/ = Some(Success(SuccessfulEvaluation((),List(),List())))

scala> kernel.process("Newton.sqrt(2)") // using classpath function
res9: /**/ = Some(Success(SuccessfulEvaluation(1.414213562373095,List(),List())))

scala> kernel.process("import ammonite.kernel.testcode.Mutable") // bringing object into scope
res10: /**/ = Some(Success(SuccessfulEvaluation((),List(),List())))

scala> kernel.process("Mutable.mutableInt") // reading the object
res11: /**/ = Some(Success(SuccessfulEvaluation(0,List(),List())))

scala> kernel.process("Mutable.mutableInt = 42") // modifying the object
res12: /**/ = Some(Success(SuccessfulEvaluation((),List(),List())))

scala> kernel.process("Mutable.mutableInt") // reading the object again
res13: /**/ = Some(Success(SuccessfulEvaluation(42,List(),List())))

scala> kernelBkp.process("ammonite.kernel.testcode.Mutable.mutableInt") // visible to others
res14: /**/ = Some(Success(SuccessfulEvaluation(42,List(),List())))

scala> kernel.loadIvy("com.simianquant", "typequux_2.11", "0.2.0") // loading external library
res15: scalaz.ValidationNel[ammonite.kernel.LogError,Unit] = Success(())

scala> kernel.process("import typequux._; import typequux._") // bringing stuff into scope
res16: /**/ = Some(Success(SuccessfulEvaluation((),List(),List())))

scala> kernel.process("""val p = 3 :+: true :+: "asdf" :+: false :+: 'k' :+: () :+: 13 :+: 9.3 :+: HNil""")
res17: /**/ = Some(Success(SuccessfulEvaluation((),List(),List())))

scala> kernel.process("val idxd = p.t[String]") // type indexing
res18: /**/ = Some(Success(SuccessfulEvaluation((),List(),List())))

scala> kernel.process("idxd.before")
res19: /**/ = Some(Success(SuccessfulEvaluation(3 :+: true :+: HNil,List(),List())))

scala> kernel.process("idxd.at")
res20: /**/ = Some(Success(SuccessfulEvaluation(asdf,List(),List())))

scala> kernel.process("idxd.updated(19)")
res21: /**/ = Some(Success(SuccessfulEvaluation(3 :+: true :+: 19 :+: false :+: k :+: () :+: 13 :+: 9.3 :+: HNil,List(),List())))

scala> kernelBkp.process("import typequux._") // different instance, does not have dependency loaded
res22: /**/ =
Some(Failure(NonEmpty[LogError(_ReplKernel1.sc:1: not found: value typequux
import typequux._
       ^)]))
```


#### Java

The `compat` package provides java friendly wrapper to the API. The functionality is encapsulated in `ReplKernelCompat`. Refer to the [java example project](https://github.com/harshad-deo/ammonite-kernel-java-example) for a detailed example.

Changelog
----

* 0.1 Initial release
* 0.2 Add `compat` package to make interop with java easier
* 0.3 Add support for scala 2.12
* 0.3.1 Bump scala version to 2.12.6
* 0.4.0 Add support for timeout
* 0.4.1 Bump version of coursier, switch to sbt 1.1.5
