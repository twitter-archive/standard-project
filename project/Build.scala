import sbt._
import Keys._

object StandardProjectPlugin extends Build {
  lazy val root = Project(id = "standard-project2",
                          base = file("."))
  .settings(
    organization := "com.twitter",
    name := "standard-project2",
    version := "0.0.1-SNAPSHOT",
    sbtPlugin := true,
    libraryDependencies ++= Seq (
      "ivysvn" % "ivysvn" % "2.1.0" from "http://maven.twttr.com/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar",
      "org.markdownj" % "markdownj" % "0.3.0-1.0.2b4",
      "org.freemarker" % "freemarker" % "2.3.16"
    ),
    credentials += Credentials(Path.userHome / ".artifactory-credentials"),
    publishTo <<= (version) { version: String =>
      val artifactory = "http://artifactory.local.twitter.com/"
      if (version.trim.endsWith("SNAPSHOT")) Some("snapshots" at artifactory + "libs-snapshots-local/") 
      else                                   Some("releases"  at artifactory + "libs-releases-local/")
    }
  )
  .settings(ScriptedPlugin.scriptedSettings: _*)
}
