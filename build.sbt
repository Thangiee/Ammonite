lazy val ammonitekernel = project
  .in(file("."))
  .settings(
    name := "ammonite-kernel",
    organization := "com.simianquant",
    organizationName := "simianquant",
    version := "0.4.1-SNAPSHOT",
    scalaVersion := "2.12.6",
    crossScalaVersions := Seq("2.11.11", "2.12.6"),
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
      "com.lihaoyi" %% "scalaparse" % "0.4.2",
      "org.scalaz" %% "scalaz-core" % "7.2.9",
      "io.get-coursier" %% "coursier" % "1.0.3",
      "io.get-coursier" %% "coursier-cache" % "1.0.3",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    ),
    javaOptions += "-Xmx4G",
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/harshad-deo/Ammonite"),
        "scm:git@github.com:harshad-deo/Ammonite.git"
      )),
    developers := List(
      Developer(
        id = "harshad-deo",
        name = "Harshad Deo",
        email = "subterranean.hominid@gmail.com",
        url = url("https://github.com/harshad-deo")
      )
    ),
    description := "Stripped down version of Ammonite",
    licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/harshad-deo/Ammonite")),
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

//   Project(
//   "ammonite-kernel-root",
//   file("."),
//   settings = Defaults.coreDefaultSettings ++ Seq(

//     // scalacOptions in (Compile, doc) ++= baseDirectory.map { (bd: File) =>
//     //   Seq[String](
//     //     "-sourcepath",
//     //     bd.getAbsolutePath,
//     //     "-doc-source-url",
//     //     "https://github.com/harshad-deo/ammonite/tree/master€{FILE_PATH}.scala"
//     //   )
//     // },
//     resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",

//     autoCompilerPlugins := true,
//     ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
//     logBuffered in Test := false,
//     javaOptions += "-Xmx4G",
//     testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
//     publishMavenStyle := true,
//     publishArtifact in Test := false,
//     publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
//     pomIncludeRepository := { _ =>
//       false
//     },
//     pomExtra := (<url>https://github.com/harshad-deo/Ammonite</url>
//       <licenses>
//         <license>
//           <name>Apache-2</name>
//           <url>http://www.apache.org/licenses/LICENSE-2.0</url>
//           <distribution>repo</distribution>
//         </license>
//       </licenses>
//       <scm>
//         <connection>scm:git:git@github.com:harshad-deo/ammonite.git</connection>
//         <developerConnection>scm:git:git@github.com:harshad-deo/ammonite.git</developerConnection>
//         <url>git@github.com:harshad-deo/ammonite.git</url>
//       </scm>
//       <developers>
//         <developer>
//           <id>harshad-deo</id>
//           <name>Harshad Deo</name>
//           <url>https://github.com/harshad-deo</url>
//         </developer>
//       </developers>)
//   )
// )

lazy val scratch = project
  .in(file("scratch"))
  .settings(
    name := "scratch",
    organization := "com.simianquant",
    scalaVersion := "2.12.6",
    fork := true
  )
  .dependsOn(ammonitekernel)
