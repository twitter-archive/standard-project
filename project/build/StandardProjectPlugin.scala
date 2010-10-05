import java.io.{File, FileReader}
import java.util.Properties
import fm.last.ivy.plugins.svnresolver.SvnResolver
import scala.collection.jcl
import _root_.sbt._

// TODO: somehow link on the real SubversionPublisher in the main source tree
class StandardProjectPlugin(info: ProjectInfo) extends PluginProject(info) with SubversionPublisher {
  override def disableCrossPaths = true

  val env = jcl.Map(System.getenv())

  override def subversionRepository = env.get("PUBLISH_SVN") match {
    case Some(s) => Some("http://svn.local.twitter.com/maven-public")
    case _ => None
  }

  val ivySvn = "ivysvn" % "ivysvn" % "2.1.0" from "http://maven.twttr.com/ivysvn/ivysvn/2.1.0/ivysvn-2.1.0.jar"

  override def managedStyle = ManagedStyle.Maven
  def artifactoryRoot = "http://artifactory.local.twitter.com"
  def snapshotDeployRepo = "libs-snapshots-local"
  def releaseDeployRepo = "libs-releases-local"

  Credentials(Path.userHome / ".ivy2" / "twitter-credentials", log)
  val publishTo = if (version.toString.endsWith("SNAPSHOT")) {
    "Twitter Artifactory" at (artifactoryRoot + "/" + snapshotDeployRepo)
  } else {
    "Twitter Artifactory" at (artifactoryRoot + "/" + releaseDeployRepo)
  }

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
}
