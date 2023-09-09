val projectName = "pi-clock"

version := "2.0.0"
scalaVersion := "2.13.2"
organization := "com.buzuli"
organizationName := "Buzuli Bytes"

val versions = {
  object v {
    val akka = "2.6.8"
    val pi4j = "2.3.0"
    val sttp = "3.3.13"
  }

  v
}

/*
lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "test",
  )
  .enablePlugins(AssemblyPlugin)
*/

libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1"

// https://mvnrepository.com/artifact/com.pi4j/pi4j-core
libraryDependencies += "com.pi4j" % "pi4j-core" % versions.pi4j

// https://mvnrepository.com/artifact/com.pi4j/pi4j-plugin-raspberrypi
libraryDependencies += "com.pi4j" % "pi4j-plugin-raspberrypi" % versions.pi4j

// https://mvnrepository.com/artifact/com.pi4j/pi4j-plugin-pigpio
libraryDependencies += "com.pi4j" % "pi4j-plugin-pigpio" % versions.pi4j

// https://mvnrepository.com/artifact/com.pi4j/pi4j-plugin-linuxfs
//libraryDependencies += "com.pi4j" % "pi4j-plugin-linuxfs" % versions.pi4j


// https://mvnrepository.com/artifact/com.softwaremill.sttp.client/core
libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % versions.sttp
libraryDependencies += "com.softwaremill.sttp.client3" %% "akka-http-backend" % versions.sttp

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-actor
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % versions.akka

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-stream
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % versions.akka

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.2.0"

// https://mvnrepository.com/artifact/com.typesafe.play/play-json
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.0"

// https://mvnrepository.com/artifact/com.typesafe.scala-logging/scala-logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"

// https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.10"

// Helpful when testing (recommended by scalatest)
Test / logBuffered := false

// The single Java source acts as the entry point for our plugin
compileOrder := CompileOrder.ScalaThenJava

// Target Java SE 11
scalacOptions += "-target:jvm-11"
javacOptions ++= Seq("-source", "1.11", "-target", "1.11", "-Xlint")

val gitInfo = {
  import scala.sys.process._

  val gitHash: String = ("git rev-parse --verify HEAD" !!) trim
  val gitDirty: Boolean = "git diff --quiet" ! match {
    case 0 => false
    case _ => true
  }

  (gitHash, gitDirty)
}

def buildArtifactName(extension: String = ".jar") = {
  val (gitHash, gitDirty) = gitInfo
  val dirtyStr = if (gitDirty) "-dirty" else ""
  val name = s"${projectName}-${version}-${gitHash}${dirtyStr}${extension}"

  name
}

artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  buildArtifactName(s".${artifact.extension}")
}

assembly / mainClass := Some("com.buzuli.clock.Main")

assembly / assemblyMergeStrategy := {
  case PathList(path, xs @ _*) if path.startsWith("jackson-") => MergeStrategy.last
  case PathList("META-INF", "Main-Class", "com.buzuli.clock.Main") => MergeStrategy.first
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case PathList("reference.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}

//assembly / assemblyJarName := {
//  buildArtifactName()
//}
