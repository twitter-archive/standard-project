import java.io.File
import sbt._


class StandardProjectPlugin(info: ProjectInfo) extends PluginProject(info) {
  override def disableCrossPaths = true
  override def ivyCacheDirectory = Some(Path.fromFile(new File(System.getProperty("user.home"))) / ".ivy2-sbt" ##)

  val ivySvn = "ivysvn" % "ivysvn" % "2.1.0" from "http://twitter.github.com/repo/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>

  Credentials(Path.userHome / ".ivy2" / "credentials", log)
  val publishTo = "nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
}
