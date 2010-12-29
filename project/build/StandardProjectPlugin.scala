import java.io.{File, FileReader}
import java.util.Properties
import fm.last.ivy.plugins.svnresolver.SvnResolver
import scala.collection.jcl
import _root_.sbt._

// TODO: somehow link on the real SubversionPublisher in the main source tree
class StandardProjectPlugin(info: ProjectInfo) extends PluginProject(info) with SubversionPublisher {
  override def disableCrossPaths = true

  val env = jcl.Map(System.getenv())

  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")

  val ivySvn = "ivysvn" % "ivysvn" % "2.1.0" from "http://maven.twttr.com/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar"

  override def managedStyle = ManagedStyle.Maven
  def artifactoryRoot = "http://artifactory.local.twitter.com"
  def snapshotDeployRepo = "libs-snapshots-local"
  def releaseDeployRepo = "libs-releases-local"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
}
