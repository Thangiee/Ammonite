Ammonite-Kernel
===

A stripped-down fork of [Ammonite](https://github.com/lihaoyi/Ammonite) designed for scala and java applications that need to compile and execute scala code at runtime.

[![Build Status](https://travis-ci.org/harshad-deo/Ammonite.svg?branch=master)](https://travis-ci.org/harshad-deo/Ammonite)
[![Build status](https://ci.appveyor.com/api/projects/status/elg05ga0wo3ds0wx?svg=true)](https://ci.appveyor.com/project/harshad-deo/ammonite)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.simianquant/ammonite-kernel_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.simianquant/ammonite-kernel_2.11)
[![Coverage Status](https://coveralls.io/repos/github/harshad-deo/Ammonite/badge.svg?branch=master)](https://coveralls.io/github/harshad-deo/Ammonite?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e249028e7b5c445982d5d39d97d1e371)](https://www.codacy.com/app/subterranean-hominid/Ammonite?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=harshad-deo/Ammonite&amp;utm_campaign=Badge_Grade)
[![Gitter](https://badges.gitter.im/harshad-deo/typequux.svg)](https://gitter.im/harshad-deo/typequux?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Scaladoc](http://javadoc-badge.appspot.com/com.simianquant/ammonite-kernel_2.11.svg?label=scaladoc)](http://javadoc-badge.appspot.com/com.simianquant/ammonite-kernel_2.11)

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
libraryDependencies += "com.simianquant" %% "ammonite-kernel" % "0.2.1"
```

#### Gradle

```groovy
compile 'com.simianquant:ammonite-kernel_2.11:0.2.1'
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

#### Java

Refer to the [java example](https://github.com/harshad-deo/ammonite-kernel-java-example) project for a more detailed example.

Changelog
----

* 0.1 Initial release
* 0.2 Add `compat` package to make interop with java easier


