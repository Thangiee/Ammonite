lazy val root = Project(
  "ammonite-kernel-root",
  file("."),
  settings = Defaults.coreDefaultSettings ++ Seq(
      name := "ammonite-kernel",
      organization := "com.simianquant",
      scalaVersion := "2.11.8",
      fork := true,
      version := "0.2.1",
      scalacOptions ++= Seq("-Ywarn-unused",
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
                            "-Ywarn-infer-any"),
      scalacOptions in (Compile, doc) ++= Seq(
        "-author",
        "-groups",
        "-implicits"
      ),
      scalacOptions in (Compile, doc) <++= baseDirectory.map { (bd: File) =>
      Seq[String](
        "-sourcepath",
        bd.getAbsolutePath,
        "-doc-source-url",
        "https://github.com/harshad-deo/ammonite/tree/master€{FILE_PATH}.scala"
      )
    },
      resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies ++= Seq("org.scala-lang" % "scala-compiler" % scalaVersion.value,
                                  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
                                  "com.lihaoyi" %% "scalaparse" % "0.3.7",
                                  "org.scalaz" %% "scalaz-core" % "7.2.6",
                                  "biz.enef" %% "slogging" % "0.5.0",
                                  "org.slf4j" % "slf4j-simple" % "1.7.21",
                                  "io.get-coursier" %% "coursier" % "1.0.0-M14",
                                  "io.get-coursier" %% "coursier-cache" % "1.0.0-M14",
                                  "org.scalatest" %% "scalatest" % "3.0.0" % "test"),
      autoCompilerPlugins := true,
      addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.15"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      logBuffered in Test := false,
      javaOptions += "-Xmx4G",
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
      publishMavenStyle := true,
      publishArtifact in Test := false,
      publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
      pomIncludeRepository := { _ =>
      false
    },
      pomExtra := (<url>https://github.com/harshad-deo/Ammonite</url>
      <licenses>
        <license>
          <name>Apache-2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:git@github.com:harshad-deo/ammonite.git</connection>
        <developerConnection>scm:git:git@github.com:harshad-deo/ammonite.git</developerConnection>
        <url>git@github.com:harshad-deo/ammonite.git</url>
      </scm>
      <developers>
        <developer>
          <id>harshad-deo</id>
          <name>Harshad Deo</name>
          <url>https://github.com/harshad-deo</url>
        </developer>
      </developers>)
    )
)
