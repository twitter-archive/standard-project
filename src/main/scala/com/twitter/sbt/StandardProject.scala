package com.twitter.sbt

import _root_.sbt._
import java.io.{FileWriter, File}
import java.util.{Date, Properties}
import java.util.jar.Attributes
import java.text.SimpleDateFormat
import scala.collection.jcl



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

  // override me for releases!
  def releaseBuild = false

  // maven repositories
  val ibiblioRepository  = "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/"
  val jbossRepository    = "jboss" at "http://repository.jboss.org/maven2/"
  val lagRepository      = "lag.net" at "http://www.lag.net/repo/"
  val twitterRepository  = "twitter.com" at "http://www.lag.net/nest/"
  val powerMock          = "powermock-api" at "http://powermock.googlecode.com/svn/repo/"
  val scalaToolsReleases = "scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val scalaToolsTesting  = "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/testing/"
  val reucon             = "reucon" at "http://maven.reucon.com/public/"
  val oauthDotNet        = "oauth.net" at "http://oauth.googlecode.com/svn/code/maven"
  val javaDotNet         = "download.java.net" at "http://download.java.net/maven/2/"
  val atlassian          = "atlassian" at "https://m2proxy.atlassian.com/repository/public/"

  // make a build.properties file and sneak it into the packaged jar.
  def buildPackage = organization + "." + name
  def packageResourcesPath = buildPackage.split("\\.").foldLeft(mainResourcesOutputPath ##) { _ / _ }
  def buildPropertiesPath = packageResourcesPath / "build.properties"
  override def packagePaths = super.packagePaths +++ buildPropertiesPath

  override def packageAction = super.packageAction dependsOn(testAction, writeBuildProperties)

  def writeBuildPropertiesTask = task {
    packageResourcesPath.asFile.mkdirs()
    val buildProperties = new Properties
    buildProperties.setProperty("name", name)
    buildProperties.setProperty("version", version.toString)
    buildProperties.setProperty("build_name", timestamp)
    currentRevision.foreach(buildProperties.setProperty("build_revision", _))
    val fileWriter = new FileWriter(buildPropertiesPath.asFile)
    buildProperties.store(fileWriter, "")
    fileWriter.close()
    None
  }

  lazy val writeBuildProperties = writeBuildPropertiesTask dependsOn(copyResources)





  // publishing stuff
  system("ivy.checksums")(StringFormat).update("sha1,md5")
  val distResolver = Resolver.file("dist", ("dist" / "repo").asFile)
  val publishConfig = new DefaultPublishConfiguration("dist", "release", true)
  lazy val deliverDist = deliverTask(deliverIvyModule, publishConfig, true) dependsOn(`package`)
  lazy val publishDist = publishTask(publishIvyModule, publishConfig) dependsOn(deliverDist)

  override def managedStyle = ManagedStyle.Maven

  def scriptPath = sourcePath / "scripts"

  lazy val stagingPath = outputPath / "dist-stage"
  lazy val cleanStagingTask = cleanTask(stagingPath)
  lazy val stageLibsForDistTask = copyTask(dependentJars, stagingPath / "lib")
  lazy val stageScriptsForDistTask = copyTask((scriptPath ##) ** "*", stagingPath / "scripts")
  lazy val stageConfigForDistTask = copyTask((configPath ##) ** "*", stagingPath / "config")
  lazy val stageForDistTask = stageConfigForDistTask dependsOn(stageScriptsForDistTask) dependsOn(stageLibsForDistTask)



  def distPaths = (stagingPath ##) ** "*" +++ ((outputPath ##) / defaultJarName)
  lazy val distAction = zipTask(distPaths, "dist", distZipName) dependsOn(stageForDistTask) dependsOn(writeBuildProperties) dependsOn(cleanStagingTask) dependsOn(packageAction)

  
  
  
  
  // build the executable jar's classpath.
  // (why is it necessary to explicitly remove the target/{classes,resources} paths? hm.)
  def dependentJars = publicClasspath +++ mainDependencies.scalaJars --- mainCompilePath ---
    mainResourcesOutputPath
  def dependentJarNames = dependentJars.getFiles.map(_.getName).filter(_.endsWith(".jar"))
  override def manifestClassPath = Some(dependentJarNames.map { "libs/" + _ }.mkString(" "))

  def distName = if (releaseBuild) (name + "-" + version) else name
  def distPath = "dist" / distName ##

  def configPath = "config" ##
  def configOutputPath = distPath / "config"

  def distZipName = {
    val revName = currentRevision.map(_.substring(0, 8)).getOrElse(version)
    "%s-%s.zip".format(name, if (releaseBuild) version else revName)
  }

  /**
   * copy into dist:
   * - packaged jar
   * - pom file for export
   * - dependent libs
   * - config files
   * - scripts
   */
  def packageDistTask = task {
    distPath.asFile.mkdirs()
    (distPath / "libs").asFile.mkdirs()
    configOutputPath.asFile.mkdirs()

    FileUtilities.copyFlat(List(jarPath), distPath, log).left.toOption orElse
      FileUtilities.copyFlat(dependentJars.get, distPath / "libs", log).left.toOption orElse
      FileUtilities.copy((configPath ***).get, configOutputPath, log).left.toOption orElse
      FileUtilities.copy(((outputPath ##) ** "*.pom").get, distPath, log).left.toOption orElse
      FileUtilities.zip((("dist" ##) / distName).get, "dist" / distZipName, true, log).left.toOption
  }

  lazy val packageDist = packageDistTask dependsOn(`package`, makePom)

  // clean: needs to rm -rf dist/

  // generate scripts
  /*
  <target name="generate-scripts" depends="prepare" if="generate.scripts">
    <pathconvert refid="deps.path" property="classpath" />
    <pathconvert refid="test.path" property="test.classpath" />
    <pathconvert refid="deps.path" property="deps.path.dist-format">
      <chainedmapper>
        <flattenmapper />
        <globmapper from="*" to="$${DIST_HOME}/libs/ *" />
      </chainedmapper>
    </pathconvert>

    <!-- delete dir="${basedir}/target/scripts" /-->
    <mkdir dir="${dist.dir}/scripts" />
    <copy todir="${dist.dir}/scripts" overwrite="true">
      <fileset dir="${basedir}/src/scripts" />
      <filterset>
        <filter token="CLASSPATH" value="${classpath}:${target.dir}/classes" />
        <filter token="TEST_CLASSPATH" value="${test.classpath}:${target.dir}/classes:${target.dir}/test-classes" />
        <filter token="DIST_CLASSPATH" value="${deps.path.dist-format}:$${DIST_HOME}/${jar.name}.jar" />
        <filter token="TARGET" value="${target.dir}" />
        <filter token="DIST_NAME" value="${dist.name}" />
      </filterset>
    </copy>
    <copy todir="${dist.dir}/scripts" overwrite="true" failonerror="false">
      <fileset dir="${target.dir}/gen-rb" />
    </copy>
    <chmod dir="${dist.dir}/scripts" includes="*" perm="ugo+x" />
  </target>
  */

}
