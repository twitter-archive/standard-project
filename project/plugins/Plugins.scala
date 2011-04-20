import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val ivySvn = "ivysvn" % "ivysvn" % "2.1.0" from "http://maven.twttr.com/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar"

  val sbtIdeaRepo = "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"
  val sbtIdea = "com.github.mpeltonen" % "sbt-idea-plugin" % "0.3.0"
  val twitterRepo = "twitter-public-repo" at "http://maven.twttr.com/"

  val standardProject = "com.twitter" % "standard-project" % "0.11.12"
}
