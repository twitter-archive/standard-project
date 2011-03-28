package com.twitter.sbt

/**
 * ProjectDependencies add explicit project dependencies
 * ala. adhoc-inlines, but it assumes that all projects in the project
 * dependency DAG are also ProjectDependency. This simplifies the
 * implementation and makes it more robust.
 */

import scala.collection.mutable.{HashSet, HashMap}

import _root_.sbt._

trait ProjectDependencies
  extends BasicManagedProject
  with ProjectCache
{
  private[this] lazy val projectDependencies = new HashSet[ProjectDependency]

  override def shouldCheckOutputDirectories = false

  case class ProjectDependency(relPath: String, name: String) {
    def projectPath: Path = Path.fromFile("..") / relPath

    def resolveProject: Option[Project] = {
      projectCache("project:%s:%s".format(projectPath.toString, name)) {
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
  }

  implicit def stringToAlmostDependency(relPath: String) = new {
    def ~(name: String): ProjectDependency = ProjectDependency(relPath, name)
  }

  implicit def stringToDependency(relPath: String): ProjectDependency =
    ProjectDependency(relPath, relPath)

  def projectDependencies(deps: ProjectDependency*) {
    projectDependencies ++= deps
  }


  private[this] var subProjectsInitialized = false

  override def subProjects = {
    if (!subProjectsInitialized) {
      super.subProjects foreach { case (_, p) =>
        setProjectCacheStoreInProject(p, projectCacheStore)
      }
      subProjectsInitialized = true
    }

    val projects = projectDependencies map { dep =>
      val project = dep.resolveProject
      if (!project.isDefined) {
        log.error("could not find dependency %s".format(dep))
        System.exit(1)
      }

      dep.name -> project.get
    }

    Map() ++ super.subProjects ++ projects
  }

  /**
   * Utilities / debugging.
   */
  lazy val showDependencies = task {
    log.info("Library dependencies:")
    libraryDependencies foreach { dep => log.info("  %s".format(dep)) }
    
    log.info("Project dependencies:")
    projectDependencies foreach { dep => log.info("  %s".format(dep)) }

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

  override def managedClasspath(config: Configuration): PathFinder = {
    super.managedClasspath(config) filter { path =>
      println("PATH %s".format(path))
      true
    }
  }
}

// Mixin for filtering deps out of the classpath?  Yes.  with a filter
// function.