package com.twitter.sbt

import _root_.sbt._
import java.io.File

trait StandardManagedProject extends BasicManagedProject
  with SourceControlledProject
  with ReleaseManagement
  with Versions
  with PublishLocalWithMavenStyleBasePattern
  with Environmentalist
{
  override def disableCrossPaths = true
  override def managedStyle = ManagedStyle.Maven
}

class StandardProject(info: ProjectInfo) extends DefaultProject(info)
  with StandardManagedProject
  with DependencyChecking
  with BuildProperties
  with CompileThrift
{
  override def dependencyPath = "libs"

  // override ivy cache
  override def ivyCacheDirectory = environment.get("SBT_CACHE").map { cacheDir =>
    Path.fromFile(new File(cacheDir))
  }

  // local repositories
  val localLibs = Resolver.file("local-libs", new File("libs"))(Patterns("[artifact]-[revision].[ext]")) transactional()

  // need to add mainResourcesOutputPath so the build.properties file can be found.
  override def consoleAction = interactiveTask {
    val console = new Console(buildCompiler)
    val classpath = consoleClasspath +++ mainResourcesOutputPath
    console(classpath.get, compileOptions.map(_.asString), "", log)
  } dependsOn(writeBuildProperties)

  // need to add mainResourcesOutputPath so the build.properties file can be found.
  override def runAction = task { args => runTask(getMainClass(true), runClasspath +++ mainResourcesOutputPath, args) dependsOn(compile, writeBuildProperties) }

  override def compileOrder = CompileOrder.JavaThenScala

  // turn on more warnings.
  override def compileOptions = super.compileOptions ++ Seq(Unchecked) ++ compileOptions("-deprecation")

  override def testOptions = {
    (environment.get("NO_TESTS") orElse environment.get("NO_TEST")).toList
      .map(_ => TestFilter(_ => false)) ++ super.testOptions
  }

  override def packageAction = super.packageAction dependsOn(testAction)

  log.info("Standard project rules " + BuildInfo.version + " loaded (" + BuildInfo.date + ").")
}

class StandardParentProject(info: ProjectInfo) extends ParentProject(info) with StandardManagedProject {
  override def usesMavenStyleBasePatternInPublishLocalConfiguration = false
}

/**
 * Nothing special here yet, but this class could accumulate traits that
 * are specific to libraries
 */
class StandardLibraryProject(info: ProjectInfo) extends StandardProject(info)

/**
 * A standard project type for building services.
 */
class StandardServiceProject(info: ProjectInfo) extends StandardProject(info)
  with PackageDist
