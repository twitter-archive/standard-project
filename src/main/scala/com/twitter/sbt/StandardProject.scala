package com.twitter.sbt

import _root_.sbt._
import java.io.File
import java.util.jar.Attributes
import net.lag.configgy.Configgy

class StandardProject(info: ProjectInfo) extends DefaultProject(info) {
  override def dependencyPath = "lib"
  override def disableCrossPaths = true

  // configgy
  val configFile = (Path.userHome / ".sbtrc").asFile
  if (configFile.exists()) {
    Configgy.configure(configFile.getAbsolutePath())
  }
  val config = Configgy.config

  // override ivy cache
  override def ivyCacheDirectory = config.getString("ivy.cache").map { cacheDir =>
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

  log.info("Standard project rules loaded (2010-04-20).")

  // publishing stuff
  override def managedStyle = ManagedStyle.Maven

  def distPath = {
    ((outputPath ##) / defaultJarName) +++
    mainResources +++
    mainDependencies.scalaJars +++
    descendents(info.projectPath, "*.sh") +++
    descendents(info.projectPath, "*.awk") +++
    descendents(info.projectPath, "*.rb") +++
    descendents(info.projectPath, "*.conf") +++
    descendents(info.projectPath / "lib" ##, "*.jar") +++
    descendents(managedDependencyRootPath / "compile" ##, "*.jar")
  }

  def packageWithDepsTask = {
    def dependantJars      = {
      descendents(managedDependencyRootPath / "compile" ##, "*.jar") +++
      (info.projectPath / "lib" ##) ** "*.jar"
    }

    val targetDepsPath = outputPath / "lib-deps"
    val cleanCopiedLibsTask = cleanTask(targetDepsPath)
    val copyLibsTask = copyTask(dependantJars, targetDepsPath / "lib") dependsOn cleanCopiedLibsTask

    val depsPath =
      ((outputPath ##) / defaultJarName) +++
      mainDependencies.scalaJars +++
      ((targetDepsPath ##) ** "*.jar")

    val classPath = Some(super.manifestClassPath.getOrElse("") + (depsPath.getRelativePaths.mkString(" ")))
    val packageOptions =
      getMainClass(false).map(MainClass(_)).toList :::
      classPath.map(cp => ManifestAttributes((Attributes.Name.CLASS_PATH, cp))).toList
    packageTask(packagePaths +++ ((targetDepsPath ##) ** "*.jar"), jarPath, packageOptions).dependsOn(testAction) dependsOn(copyLibsTask)
  }
  lazy val packageWithDeps = packageWithDepsTask

  override def manifestClassPath = Some(
    distPath.getFiles
    .filter(_.getName.endsWith(".jar"))
    .map(_.getName).mkString(" ")
  )

  def distName = "%s-%s.zip".format(name, version)
  lazy val zip = zipTask(distPath, "dist", distName) dependsOn (`package`) describedAs("Zips up the project.")
}
