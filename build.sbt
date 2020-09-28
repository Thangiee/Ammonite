lazy val ammonitekernel = project
  .in(file("."))
  .settings(
    name := "ammonite-kernel",
    organization := "com.simianquant",
    organizationName := "simianquant",
    version := "0.4.3",
    scalaVersion := "2.12.12",
    crossScalaVersions := Seq("2.12.12"),
    fork := true,
    scalacOptions ++= Seq(
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-Ywarn-inaccessible",
      "-Ywarn-dead-code",
      "-explaintypes",
      "-Xlog-reflective-calls",
      "-Ywarn-value-discard",
      "-Xlint",
      "-deprecation",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-feature",
      "-unchecked",
      "-Xfuture",
      "-encoding",
      "UTF-8",
      "-Ywarn-infer-any"
    ),
    scalacOptions in (Compile) ++= Seq(scalaVersion.value match {
      case x if x.startsWith("2.12.") => "-target:jvm-1.8"
      case x => "-target:jvm-1.6"
    }),
    scalacOptions in (Compile, doc) ++= Seq(
      "-author",
      "-groups",
      "-implicits"
    ),
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.lihaoyi" %% "scalaparse" % "2.3.0",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    ),
    javaOptions += "-Xmx4G",
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/SimianQuant/Ammonite"),
        "scm:git@github.com:SimianQuant/Ammonite.git"
      )
    ),
    developers := List(
      Developer(
        id = "harshad-deo",
        name = "Harshad Deo",
        email = "harshad@simianquant.com",
        url = url("https://github.com/harshad-deo")
      )
    ),
    description := "Stripped down version of ammonite",
    licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/SimianQuant/Ammonite")),
    pomIncludeRepository := { _ =>
      false
    },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true
  )

lazy val scratch = project
  .in(file("scratch"))
  .settings(
    name := "scratch",
    organization := "com.simianquant",
    scalaVersion := "2.12.12",
    fork := true
  )
  .dependsOn(ammonitekernel)
