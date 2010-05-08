import java.io.File
import sbt._


class StandardProjectPlugin(info: ProjectInfo) extends PluginProject(info) {
  override def disableCrossPaths = true
  override def ivyCacheDirectory = Some(Path.fromFile(new File(System.getProperty("user.home"))) / ".ivy2-sbt" ##)
  val publishTo = Resolver.sftp("green.lag.net", "green.lag.net", "/web/nest")
  val ivySvn = "ivysvn" % "ivysvn" % "2.1.0" from "http://www.lag.net/nest/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar"
}
