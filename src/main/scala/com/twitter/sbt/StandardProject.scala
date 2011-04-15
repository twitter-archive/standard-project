package com.twitter.sbt

import _root_.sbt._
import java.io.File

trait StandardManagedProject extends BasicManagedProject
  with SourceControlledProject
  with ReleaseManagement
  with Versions
  with Environmentalist
{
  override def disableCrossPaths = true
  override def managedStyle = ManagedStyle.Maven

  // resolvers that will be used even if we're going through a proxy resolver
  def localRepos: Set[Resolver] = Set()
}

class StandardProject(info: ProjectInfo) extends DefaultProject(info)
  with StandardManagedProject
  with DependencyChecking
  with PublishLocalWithMavenStyleBasePattern
  with BuildProperties
  with IntransitiveCompiles
{
  override def dependencyPath = "libs"

  // override ivy cache
  override def ivyCacheDirectory = environment.get("SBT_CACHE").map { cacheDir =>
    Path.fromFile(new File(cacheDir))
  }

  // local repositories
  val localLibs = Resolver.file("local-libs", new File("libs"))(Patterns("[artifact]-[revision].[ext]")) transactional()
  override def localRepos = super.localRepos + localLibs

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
  override def compileOptions = super.compileOptions ++
    Seq(Unchecked) ++
    compileOptions("-encoding", "utf8") ++
    compileOptions("-deprecation")

  override def testOptions = {
    (environment.get("NO_TESTS") orElse environment.get("NO_TEST")).toList
      .map(_ => TestFilter(_ => false)) ++ super.testOptions
  }

  override def packageAction = super.packageAction dependsOn(testAction)

  // Optional ramdisk.
  private[this] val ramdiskRoot = environment.get("SBT_RAMDISK_ROOT")
  private[this] val ramdiskTargetName = "target-ramdisk"
  for (ramdiskRoot <- ramdiskRoot) {
    val ramdiskPath = new File("%s/%s".format(ramdiskRoot, name))
    log.info("Compiling to ramdisk at %s".format(ramdiskPath))

    val target = new File(ramdiskTargetName)
    val canonicalPath = target.getCanonicalPath
    val absolutePath = target.getAbsolutePath

    if (target.exists && canonicalPath != ramdiskRoot) {
      if (target.isFile || absolutePath != canonicalPath) {
        log.info("Deleting existing symlink at %s".format(target))
        target.delete()
      } else {
        log.info("Removing existing directory at %s".format(target))
        FileUtilities.clean(Path.fromFile(target), log)
      }
    }

    // Make symlink.
    if (!target.exists) {
      import Process._
      log.info("Creating ramdisk build symlink %s".format(ramdiskPath))
      ramdiskPath.mkdirs()
      (execTask { "ln -s %s %s".format(ramdiskPath, ramdiskTargetName) }).run
    }
  }

  override def outputRootPath =
    if (ramdiskRoot.isDefined)
      "target-ramdisk": Path
    else
      super.outputRootPath

  // log.info("Standard project rules " + BuildInfo.version + " loaded (" + BuildInfo.date + ").")
}

class StandardParentProject(info: ProjectInfo) extends ParentProject(info)
  with StandardManagedProject
  with PublishLocalWithMavenStyleBasePattern
{
  override def usesMavenStyleBasePatternInPublishLocalConfiguration = false
}

/**
 * Nothing special here yet, but this class could accumulate traits that
 * are specific to libraries
 */
class StandardLibraryProject(info: ProjectInfo) extends StandardProject(info)
  with PackageDist

/**
 * A standard project type for building services.
 */
class StandardServiceProject(info: ProjectInfo) extends StandardProject(info)
  with PackageDist
