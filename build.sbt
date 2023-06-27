
val projectName = "pi-clock"

version := "1.0.0"
scalaVersion := "2.13.10"
organization := "com.buzuli"
organizationName := "Buzuli Bytes"

val akkaVersion = "2.6.8"
val sttpVersion = "3.3.13"
val zioVersion = "2.0.13"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1"

// https://mvnrepository.com/artifact/com.pi4j/pi4j-core
libraryDependencies += "com.pi4j" % "pi4j-core" % "1.2"

// https://mvnrepository.com/artifact/com.softwaremill.sttp.client/core
libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % sttpVersion
libraryDependencies += "com.softwaremill.sttp.client3" %% "akka-http-backend" % sttpVersion

// https://zio.dev/
libraryDependencies += "dev.zio" %% "zio" % zioVersion
libraryDependencies += "dev.zio" %% "zio-streams" % zioVersion

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-actor
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-stream
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.2.0"

// https://mvnrepository.com/artifact/com.typesafe.play/play-json
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.0"

// https://mvnrepository.com/artifact/com.typesafe.scala-logging/scala-logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"

// https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.10"

// Helpful when testing (recommended by scalatest)
logBuffered in Test := false

// The single Java source acts as the entry point for our plugin
compileOrder := CompileOrder.ScalaThenJava

// Target Java SE 8
scalacOptions += "-target:jvm-8"
javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

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

mainClass in assembly := Some("com.buzuli.clock.Main")

assemblyMergeStrategy in assembly := {
  case PathList(path, xs @ _*) if path.startsWith("jackson-") => MergeStrategy.last
  case PathList("META-INF", "Main-Class", "com.buzuli.clock.Main") => MergeStrategy.first
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case PathList("reference.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}

