import java.io.File
import sbt._


class StandardProjectPlugin(info: ProjectInfo) extends PluginProject(info) {
  override def disableCrossPaths = true
  override def ivyCacheDirectory = Some(Path.fromFile(new File(System.getProperty("user.home"))) / ".ivy2-sbt" ##)
  val configgy  = "net.lag" % "configgy" % "1.5.2"
}

