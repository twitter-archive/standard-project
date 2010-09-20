import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val ivySvn = "ivysvn" % "ivysvn" % "2.1.0" from "http://twitter.github.com/repo/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar"
}

