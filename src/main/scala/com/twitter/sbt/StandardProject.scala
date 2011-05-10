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

trait StandardTestableProject extends DefaultProject with StandardManagedProject {
  override def dependencyPath = "libs"

  // override ivy cache
  override def ivyCacheDirectory = environment.get("SBT_CACHE").map { cacheDir =>
    Path.fromFile(new File(cacheDir))
  }

  // local repositories
  val localLibs = Resolver.file("local-libs", new File("libs"))(Patterns("[artifact]-[revision].[ext]")) transactional()
  override def localRepos = super.localRepos + localLibs

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
}

class StandardProject(info: ProjectInfo) extends DefaultProject(info)
  with StandardTestableProject
  with Ramdiskable
  with DependencyChecking
  with PublishLocalWithMavenStyleBasePattern
  with PublishSourcesAndJavadocs
  with BuildProperties
  with IntransitiveCompiles
{
  // need to add mainResourcesOutputPath so the build.properties file can be found.
  override def consoleAction = interactiveTask {
    val console = new Console(buildCompiler)
    val classpath = consoleClasspath +++ mainResourcesOutputPath
    console(classpath.get, compileOptions.map(_.asString), "", log)
  } dependsOn(writeBuildProperties)

  // need to add mainResourcesOutputPath so the build.properties file can be found.
  override def runAction = task { args =>
    val classpath = runClasspath +++ mainResourcesOutputPath
    runTask(getMainClass(true), classpath, args) dependsOn(compile, writeBuildProperties)
  }

  lazy val runClass = runClassAction

  def runClassAction = task { args =>
    if (args.isEmpty) {
      task { Some("class name expected as argument") }
    } else {
      val mainClass = args(0)
      val mainArgs = args.drop(1)
      val classpath = runClasspath +++ mainResourcesOutputPath
      runTask(Some(mainClass), classpath, mainArgs)
        .dependsOn(compile, writeBuildProperties)
    }
  } describedAs("Run the main method of a specified class.")

  override def packageAction = super.packageAction dependsOn(testAction)

  /**
   * A blacklist for dependencies that cause problems. Add the name of
   * any dependency that doesn't work correctly with ensime.
   * (e.g. "standard-project")
   */
  val ensimeBlacklist: List[String] = Nil

  /**
   * Generates a .ensime file for the project and it's dependencies.
   */
  lazy val generateEnsime = task { _ => interactiveTask {
    val file = new java.io.File(info.projectDirectory, ".ensime")
    val out = new java.io.PrintWriter(new java.io.FileWriter(file))

    out.println("(")
    out.println(":project-package \"com.twitter\"")
    out.println(":compile-jars (")

    testClasspath.get foreach {
      case path if path.absolutePath.endsWith(".jar") =>
        out.println("  \"%s\"".format(path.absolutePath))
      case _ => ()
    }

    out.println("  )")
    out.println(":sources (")

    Seq("mainSourceRoots", "testSourceRoots") foreach { methodName =>
      projectClosure foreach { project =>
        try {
          if (!ensimeBlacklist.contains(project.name) || methodName != "testSourceRoots") {
            val m = project.getClass.getMethod(methodName)
            val finder = m.invoke(project).asInstanceOf[PathFinder]
            finder.get foreach { path =>
              out.println("      \"%s\"".format(path.absolutePath))
            }
          }
        } catch { case _ => () }
      }
    }
    out.println("  )")
    out.println(")")
    out.close()

    None
  } dependsOn(compile, testCompile) } describedAs("Generate a .ensime file for all projects together.")

  log.info("Standard project rules " + BuildInfo.version + " loaded (" + BuildInfo.date + ").")
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
  with PublishSourcesAndJavadocs

/**
 * A standard project type for building services.
 */
class StandardServiceProject(info: ProjectInfo) extends StandardProject(info)
  with PackageDist
  with PublishSourcesAndJavadocs
