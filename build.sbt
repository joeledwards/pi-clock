
val projectName = "pi-clock"

version := "1.0.0"
scalaVersion := "2.13.2"
organization := "com.buzuli"
organizationName := "Buzuli Bytes"

/*
lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "test",
  ).
  enablePlugins(AssemblyPlugin)
 */

libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1"

// https://mvnrepository.com/artifact/com.pi4j/pi4j-core
libraryDependencies += "com.pi4j" % "pi4j-core" % "1.2"

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
  //val name = s"${projectName}-${prestoVersion}-${gitHash}${dirtyStr}-${dateTime}${extension}"
  val name = s"${projectName}-${version}-${gitHash}${dirtyStr}${extension}"
  //println(s"PRESTO_IAM_AUTH_ARTIFACT: ${name}")

  name
}

artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  buildArtifactName(s".${artifact.extension}")
}

//assemblyJarName in assembly := {
//  buildArtifactName()
//}