package com.twitter.sbt

/**
 * ProjectDependencies add explicit project dependencies
 * ala. adhoc-inlines, but it assumes that all projects in the project
 * dependency DAG are also ProjectDependency. This simplifies the
 * implementation and makes it more robust.
 */

import scala.collection.mutable.{HashSet, HashMap}

import java.util.Properties
import java.io.{FileInputStream, FileOutputStream}
import pimpedversion._
import collection.jcl.Conversions._

import _root_.sbt._

trait ProjectDependencies
  extends BasicManagedProject
  with ProjectCache
  with ManagedClasspathFilter
  with Environmentalist
{
  private lazy val useProjectDependencies = !environment.get("NO_PROJECT_DEPS").isDefined
  private lazy val _projectDependencies = new HashSet[ProjectDependency]
  def getProjectDependencies = _projectDependencies

  override def shouldCheckOutputDirectories = false

  case class ProjectDependency(relPath: String, name: String) {
    def parentDependency: ProjectDependency =
      if (relPath == name) this
      else ProjectDependency(relPath, relPath)

    lazy val projectPath: Path = {
      val candidates = Seq(
        Path.fromFile(relPath),
        Path.fromFile("..") / relPath
      )

      val resolved = candidates.find { path => (path / "project" / "build.properties").exists }
      if (!resolved.isDefined) {
        log.error("could not find project path for (%s, %s)".format(relPath, name))
        System.exit(1)
      }

      resolved.get
    }

    def resolveProject: Option[Project] = {
      projectCache("project:%s:%s".format(relPath, name)) {
        val parentProject =
          projectCache("path:%s".format(projectPath)) { Some(project(projectPath)) }
        val foundProject = parentProject flatMap { parentProject => 
          if (parentProject.name != name) {
            // Try to find it in a subproject.
            parentProject.subProjects.find { _._2.name == name } map { _._2 }
          } else {
            Some(parentProject)
          }
        }

        foundProject foreach { setProjectCacheStoreInProject(_, projectCacheStore) }
        foundProject
      }
    }

    def resolveModuleID: Option[ModuleID] = {
      val project = info.parent getOrElse ProjectDependencies.this
      val versionsPath =
        Path.fromFile(project.info.projectPath.absolutePath) / "project" / "versions.properties"

      val prop = new Properties
      prop.load(new FileInputStream(versionsPath.toString))

      resolveProject flatMap { depProject =>
        val versionString = prop.getProperty(
          "%s/%s".format(depProject.organization, depProject.name))
        if (versionString ne null)
          Some(depProject.organization % depProject.name % versionString)
        else
          None
      }
    }
  }

  implicit def stringToAlmostDependency(relPath: String) = new {
    def ~(name: String): ProjectDependency = ProjectDependency(relPath, name)
  }

  implicit def stringToDependency(relPath: String): ProjectDependency =
    ProjectDependency(relPath, relPath)

  def projectDependencies(deps: ProjectDependency*) {
    _projectDependencies ++= deps
  }

  private[this] var subProjectsInitialized = false

  override def subProjects = {
    if (!subProjectsInitialized) {
      super.subProjects foreach { case (_, p) =>
        setProjectCacheStoreInProject(p, projectCacheStore)
      }
      subProjectsInitialized = true
    }

    if (useProjectDependencies) {
      val projects = _projectDependencies map { dep =>
        val project = dep.resolveProject
        if (!project.isDefined) {
          log.error("could not find dependency %s".format(dep))
          System.exit(1)
        }

        dep.name -> project.get
      }

      Map() ++ super.subProjects ++ projects
    } else {
      super.subProjects
    }
  }

  override def libraryDependencies =
    if (useProjectDependencies) {
      super.libraryDependencies
    } else {
      (Set() ++ _projectDependencies map { _.resolveModuleID.get }) ++ super.libraryDependencies
    }

  /**
   * Filters out dependencies that are in our DAG.
   */

  def managedDependencyFilter(organization: String, name: String): Boolean =
    !useProjectDependencies ||
    !(projectClosure exists { p => p.organization == organization && p.name == name })

  /**
   * Release management.
   */

  def lastReleasedVersion(): Option[Version] = {
    val project = info.parent getOrElse this
    val releasePropertiesPath =
      Path.fromFile(project.info.projectPath.absolutePath) / "project" / "release.properties"
    val prop = new Properties
    if (!releasePropertiesPath.exists)
      return None

    prop.load(new FileInputStream(releasePropertiesPath.toString))
    val version = prop.getProperty("version")
    if (version eq null)
      return None

    Version.fromString(version) match {
      case Left(_) => None
      case Right(v) => Some(v)
    }
  }

  lazy val updateVersions = task {
    val prop = new Properties
    val projects = _projectDependencies flatMap { _.resolveProject }

    // TODO: use builderPath?
    val project = info.parent getOrElse this
    val versionsPath =
      Path.fromFile(project.info.projectPath.absolutePath) / "project" / "versions.properties"

    // Merge the existing one when it exists.
    if (versionsPath.exists)
      prop.load(new FileInputStream(versionsPath.toString))

    projects foreach { p =>
      val m = p.getClass.getDeclaredMethod("lastReleasedVersion")
			val version = if (m ne null) {
				m.invoke(p).asInstanceOf[Option[Version]]
			} else {
				log.error("project %s is not a ReleaseManagement project".format(p.name))
				None
			}

			version foreach { version =>
				prop.setProperty(
					"%s/%s".format(p.organization, p.name),
					version.toString)
			}
    }

    val stream = new FileOutputStream(versionsPath.toString)
    prop.store(stream,"Automatically generated by ProjectDependencies")
    stream.close()

    None
  }

  /**
   * Utilities / debugging.
   */

  lazy val showDependencies = task {
    log.info("Library dependencies:")
    libraryDependencies foreach { dep => log.info("  %s".format(dep)) }

    log.info("Project dependencies:")
    _projectDependencies foreach { dep => log.info("  %s".format(dep)) }

    None
  }

  lazy val showProjectClosure = task {
    log.info("Project closure:")
    projectClosure foreach { project =>
      println("  " + project + " " + project.hashCode)
    }

    None
  }

  lazy val showManagedClasspath = task {
    managedClasspath(Configurations.Compile).get foreach { path =>
      println("> %s".format(path))
    }

    None
  }

  lazy val showLastReleasedVersion = task {
    println(lastReleasedVersion)
    None
  }
}
