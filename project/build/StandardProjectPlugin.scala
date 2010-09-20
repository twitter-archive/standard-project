import java.io.{File, FileReader}
import java.util.Properties
import fm.last.ivy.plugins.svnresolver.SvnResolver
import _root_.sbt._

// TODO: somehow link on the real SubversionPublisher in the main source tree
class StandardProjectPlugin(info: ProjectInfo) extends PluginProject(info) with SubversionPublisher {
  override def disableCrossPaths = true
  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")

  val ivySvn = "ivysvn" % "ivysvn" % "2.1.0" from "http://twitter.github.com/repo/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
}
