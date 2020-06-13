import Dependencies._

ThisBuild / scalaVersion     := "2.13.2"
ThisBuild / version          := "1.0.0"
ThisBuild / organization     := "com.buzuli"
ThisBuild / organizationName := "Buzuli Bytes"

lazy val root = (project in file("."))
  .settings(
    name := "pi-clock",
    libraryDependencies += scalaTest % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
