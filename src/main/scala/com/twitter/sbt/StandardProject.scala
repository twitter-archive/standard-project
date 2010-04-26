package com.twitter.sbt

import _root_.sbt._
import java.io.{FileWriter, File}
import java.util.{Date, Properties}
import java.util.jar.Attributes
import java.text.SimpleDateFormat
import net.lag.configgy.Configgy


class StandardProject(info: ProjectInfo) extends DefaultProject(info) with SourceControlledProject {
  override def dependencyPath = "libs"
  override def managedDependencyPath = "target" / "lib_managed" ##
  override def disableCrossPaths = true
  def timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date)

  val env = jcl.Map(System.getenv())

  // override ivy cache
  override def ivyCacheDirectory = env.get("SBT_CACHE").map { cacheDir =>
    Path.fromFile(new File(cacheDir))
  }

  // maven repositories
  val ibiblioRepository  = "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/"
  val jbossRepository    = "jboss" at "http://repository.jboss.org/maven2/"
  val lagRepository      = "lag.net" at "http://www.lag.net/repo/"
  val twitterRepository  = "twitter.com" at "http://www.lag.net/nest/"
  val powerMock          = "powermock-api" at "http://powermock.googlecode.com/svn/repo/"
  val scalaToolsReleases = "scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val scalaToolsTesting  = "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val reucon             = "reucon" at "http://maven.reucon.com/public/"
  val oauthDotNet        = "oauth.net" at "http://oauth.googlecode.com/svn/code/maven"
  val javaDotNet         = "download.java.net" at "http://download.java.net/maven/2/"
  val atlassian          = "atlassian" at "https://m2proxy.atlassian.com/repository/public/"

  override def packageAction = super.packageAction dependsOn(testAction)

  // publishing stuff
  val distResolver = Resolver.file("dist", ("dist" / "repo").asFile)
  val publishConfig = new DefaultPublishConfiguration("dist", "release", true)
  lazy val deliverDist = deliverTask(deliverIvyModule, publishConfig, true) dependsOn(`package`)
  lazy val publishDist = publishTask(publishIvyModule, publishConfig) dependsOn(deliverDist)

  override def managedStyle = ManagedStyle.Maven

  def dependantJars = {
    descendents(managedDependencyRootPath / "compile" ##, "*.jar") +++
    (info.projectPath / "lib" ##) ** "*.jar"
  }

  def scriptPath = sourcePath / "scripts"
  def configPath = (info.projectPath / "config")

  lazy val stagingPath = outputPath / "dist-stage"
  lazy val cleanStagingTask = cleanTask(stagingPath)
  lazy val stageLibsForDistTask = copyTask(dependantJars, stagingPath / "lib")
  lazy val stageScriptsForDistTask = copyTask((scriptPath ##) ** "*", stagingPath / "scripts")
  lazy val stageConfigForDistTask = copyTask((configPath ##) ** "*", stagingPath / "config")
  lazy val stageForDistTask = stageConfigForDistTask dependsOn(stageScriptsForDistTask) dependsOn(stageLibsForDistTask)

  def buildPackage = organization + "." + name

  def writeBuildPropertiesTask = task {
    val buildFile = (buildPackage.split("\\.").foldLeft(mainCompilePath)(_ / _) / "build.properties").asFile
    val buildProperties = new Properties
    buildProperties.setProperty("name", name)
    buildProperties.setProperty("version", version.toString)
    buildProperties.setProperty("build_name", timestamp)
    currentRevision.foreach(buildProperties.setProperty("build_revision", _))
    val fileWriter = new FileWriter(buildFile)
    buildProperties.store(fileWriter, "")
    fileWriter.close()
    None
  }

  lazy val writeBuildProperties = writeBuildPropertiesTask

  def distName = "%s-%s.zip".format(name, currentRevision.map(_.substring(0, 8)).getOrElse(version))
  def distPaths = (stagingPath ##) ** "*" +++ ((outputPath ##) / defaultJarName)
  lazy val distAction = zipTask(distPaths, "dist", distName) dependsOn(stageForDistTask) dependsOn(writeBuildProperties) dependsOn(cleanStagingTask) dependsOn(packageAction)

  def packageWithDepsTask = {
    val depsPath =
      ((outputPath ##) / defaultJarName) +++
      mainDependencies.scalaJars +++
      ((stagingPath ##) ** "*.jar")

    val classPath = Some(super.manifestClassPath.getOrElse("") + (depsPath.getRelativePaths.mkString(" ")))
    val packageOptions =
      getMainClass(false).map(MainClass(_)).toList :::
      classPath.map(cp => ManifestAttributes((Attributes.Name.CLASS_PATH, cp))).toList
    packageTask(packagePaths +++ ((stagingPath ##) ** "*.jar"), jarPath, packageOptions).dependsOn(testAction) dependsOn(stageLibsForDistTask)
  }

  lazy val packageWithDeps = packageWithDepsTask
}
