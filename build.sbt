name := "dash-bukkit"

organization := "org.gestern"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.6" % "test" withSources() withJavadoc(),
  "org.scalacheck" %% "scalacheck" % "1.12.5" % "test" withSources() withJavadoc(),
  "org.rogach" %% "scallop" % "1.0.1" withSources(), // command line parser
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7",

  "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
  "com.megatome.javadoc2dash" % "javadoc2dash-api" % "1.0.7",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.3.1.201605051710-r"
)

initialCommands := "import org.gestern.dashbukkit._"

