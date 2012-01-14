organization := "com.twitter"

name := "standard-project"

version := "11.0.0-SNAPSHOT"

sbtPlugin := true

libraryDependencies ++= Seq (
  "ivysvn" % "ivysvn" % "2.1.0" from "http://maven.twttr.com/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar",
  "org.markdownj" % "markdownj" % "0.3.0-1.0.2b4",
  "org.freemarker" % "freemarker" % "2.3.16"
)

seq(ScriptedPlugin.scriptedSettings: _*)
