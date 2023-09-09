val projectName = "pi-clock"

version := "2.0.0"
scalaVersion := "2.13.11"
organization := "com.buzuli"
organizationName := "Buzuli Bytes"

val versions = {
  object v {
    val akka = "2.6.8"
    val akkaHttp = "10.2.0"
    val logbackClassic = "1.2.10"
    val pi4j = "2.3.0"
    val playJson = "2.9.0"
    val scalaLogging = "3.9.4"
    val scalatest = "3.2.17"
    val sttp = "3.8.16"
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

libraryDependencies += "org.scalatest" %% "scalatest" % versions.scalatest

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
libraryDependencies += "com.typesafe.akka" %% "akka-http" % versions.akkaHttp

// https://mvnrepository.com/artifact/com.typesafe.play/play-json
libraryDependencies += "com.typesafe.play" %% "play-json" % versions.playJson

// https://mvnrepository.com/artifact/com.typesafe.scala-logging/scala-logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % versions.scalaLogging

// https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
libraryDependencies += "ch.qos.logback" % "logback-classic" % versions.logbackClassic

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

assembly / logLevel := Level.Debug

assembly / assemblyMergeStrategy := {
  case PathList(path, xs @ _*) if path.startsWith("jackson-") => MergeStrategy.last
  case PathList("META-INF", "Main-Class", "com.buzuli.clock.Main") => MergeStrategy.first
  case PathList("META-INF", "services", _*) => MergeStrategy.filterDistinctLines
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

//assembly / assemblyJarName := {
//  buildArtifactName()
//}
