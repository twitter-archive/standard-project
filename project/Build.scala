import sbt._
import Keys._

import com.twitter.sbt._

import java.io.{File, FileReader}
import java.util.Properties

import fm.last.ivy.plugins.svnresolver.SvnResolver

object StandardProjectPlugin extends Build {
  lazy val root = Project(id = "standard-project2",
                          base = file("."))
  .settings(StandardProject.newSettings: _*)
  .settings(SubversionPublisher.newSettings: _*)
  .settings(
    organization := "com.twitter",
    name := "standard-project2",
    SubversionPublisher.subversionRepository := Some("https://svn.twitter.biz/maven-public"),
    version := "0.0.3",
    sbtPlugin := true,
    libraryDependencies ++= Seq (
      "ivysvn" % "ivysvn" % "2.1.0",
      "org.markdownj" % "markdownj" % "0.3.0-1.0.2b4",
      "org.freemarker" % "freemarker" % "2.3.16"
    )
  )
  .settings(ScriptedPlugin.scriptedSettings: _*)
}
