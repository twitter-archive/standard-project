import java.io.File
import sbt._


class StandardProjectPlugin(info: ProjectInfo) extends PluginProject(info) {
  override def disableCrossPaths = true
  override def ivyCacheDirectory = Some(Path.fromFile(new File(System.getProperty("user.home"))) / ".ivy2-sbt" ##)
  val publishTo = Resolver.sftp("green.lag.net", "green.lag.net", "/web/nest")
}
