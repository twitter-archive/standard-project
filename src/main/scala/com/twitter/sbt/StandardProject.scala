package com.twitter.sbt

import _root_.sbt._
import java.io.{FileWriter, File}
import java.util.{Date, Properties}
import java.util.jar.Attributes
import java.text.SimpleDateFormat
import scala.collection.jcl


class StandardProject(info: ProjectInfo) extends DefaultProject(info) with SourceControlledProject with ReleaseManagement with Versions {
  override def dependencyPath = "libs"
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
  val localLibs = Resolver.file("local-libs", new File("libs"))(Patterns("[artifact]-[revision].[ext]")) transactional()
  val ibiblioRepository  = "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/"
  val lagRepository      = "lag.net" at "http://www.lag.net/repo/"
  val twitterRepository  = "old.twitter.com" at "http://www.lag.net/nest/"
  val nuTwitterRepository= "twitter.com" at "http://maven.twttr.com/"
  val powerMock          = "powermock-api" at "http://powermock.googlecode.com/svn/repo/"
  val scalaToolsReleases = "scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val scalaToolsTesting  = "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/testing/"
  val oauthDotNet        = "oauth.net" at "http://oauth.googlecode.com/svn/code/maven"
  val javaDotNet         = "download.java.net" at "http://download.java.net/maven/2/"
  val atlassian          = "atlassian" at "https://m2proxy.atlassian.com/repository/public/"

  val twitterPrivateRepo = if (env.get("SBT_TWITTER").isDefined) {
    new MavenRepository("twitter-private-m2", "http://binaries.local.twitter.com/maven/")
  } else {
    DefaultMavenRepository
  }

  // make a build.properties file and sneak it into the packaged jar.
  def buildPackage = organization + "." + name
  def packageResourcesPath = buildPackage.split("\\.").foldLeft(mainResourcesOutputPath ##) { _ / _ }
  def buildPropertiesPath = packageResourcesPath / "build.properties"
  override def packagePaths = super.packagePaths +++ buildPropertiesPath

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

  val WriteBuildPropertiesDescription = "Writes a build.properties file into the target folder."
  lazy val writeBuildProperties = writeBuildPropertiesTask dependsOn(copyResources) describedAs WriteBuildPropertiesDescription

  override def managedStyle = ManagedStyle.Maven

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

  def scriptsPath = "src" / "scripts" ##
  def scriptsOutputPath = distPath / "scripts"

  def distZipName = {
    val revName = currentRevision.map(_.substring(0, 8)).getOrElse(version)
    "%s-%s.zip".format(name, if (releaseBuild) version else revName)
  }

  // thrift generation.
  def compileThriftAction(lang: String) = task {
    import Process._
    outputPath.asFile.mkdirs()
    val thriftBin = env.get("THRIFT_BIN").getOrElse("thrift")
    val tasks = thriftSources.getPaths.map { path =>
      execTask { "%s --gen %s -o %s %s".format(thriftBin,lang, outputPath.absolutePath, path) }
    }
    if (tasks.isEmpty) None else tasks.reduceLeft { _ && _ }.run
  }

  def thriftSources = (mainSourcePath / "thrift" ##) ** "*.thrift"
  def thriftJavaPath = outputPath / "gen-java"
  def thriftRubyPath = outputPath / "gen-rb"

  // turn on more warnings.
  override def compileOptions = super.compileOptions ++ Seq(Unchecked)

  lazy val cleanThrift = (cleanTask(thriftJavaPath) && cleanTask(thriftRubyPath)) describedAs("Clean thrift generated folder")
  lazy val compileThriftJava = compileThriftAction("java") describedAs("Compile thrift into java")
  lazy val compileThriftRuby = compileThriftAction("rb") describedAs("Compile thrift into ruby")
  override def compileOrder = CompileOrder.JavaThenScala
  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / "gen-java" ##)

  // copy scripts.
  val CopyScriptsDescription = "Copies scripts into the dist folder."
  val copyScripts = task {
    val filters = Map(
      "CLASSPATH" -> (publicClasspath +++ mainDependencies.scalaJars).getPaths.mkString(":"),
      "TEST_CLASSPATH" -> testClasspath.getPaths.mkString(":"),
      "DIST_CLASSPATH" -> (dependentJarNames.map { "${DIST_HOME}/libs/" + _ }.mkString(":") +
        ":${DIST_HOME}/" + defaultJarName),
      "DIST_NAME" -> name,
      "VERSION" -> version.toString
    )

    scriptsOutputPath.asFile.mkdirs()
    (scriptsPath ***).filter { !_.isDirectory }.get.foreach { path =>
      val dest = Path.fromString(scriptsOutputPath, path.relativePath)
      new File(dest.absolutePath.toString).getParentFile().mkdirs()
      FileFilter.filter(path, dest, filters)
      Runtime.getRuntime().exec(List("chmod", "+x", dest.absolutePath.toString).toArray).waitFor()
    }
    None
  } named("copy-scripts") dependsOn(`compile`) describedAs CopyScriptsDescription

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
      FileUtilities.zip((("dist" / distName) ##).get, "dist" / distZipName, true, log)
  }

  val PackageDistDescription = "Creates a deployable zip file with dependencies, config, and scripts."
  lazy val packageDist = packageDistTask dependsOn(`package`, makePom, copyScripts) describedAs PackageDistDescription

  override def testOptions = {
    if (env.get("NO_TESTS").isDefined || env.get("NO_TEST").isDefined) {
      List(TestFilter(_ => false))
    } else {
      Nil
    } ++ super.testOptions
  }

  override def compileAction = super.compileAction dependsOn(compileThriftJava, compileThriftRuby)
  override def packageAction = super.packageAction dependsOn(testAction, writeBuildProperties)

  val cleanDist = cleanTask("dist" ##) describedAs("Erase any packaged distributions.")
  override def cleanAction = super.cleanAction dependsOn(cleanThrift, cleanDist)

  // allow publish-local to write maven-compatible folders.

  val ivyBasePattern = "[organisation]/[module]/[revision]/ivy-[revision](-[classifier]).[ext]"
  val localm2 = Resolver.file("localm2", new File(Resolver.userIvyRoot + "/local"))(
    Patterns(Seq(ivyBasePattern), Seq(Resolver.mavenStyleBasePattern), true))
  override def publishLocalConfiguration = new DefaultPublishConfiguration("localm2", "release", true)

  log.info("Standard project rules 0.7.10 loaded (2010-10-12).")
}
