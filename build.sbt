name := "akka-xlsx"
organization := "com.example"
scalaVersion := "2.12.3"
version := "0.1.0-SNAPSHOT"

lazy val root = (project in file(".")).settings(
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.11",
    "com.lightbend.akka" %% "akka-stream-alpakka-xml" % "0.17",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.11" % Test
  )
)
