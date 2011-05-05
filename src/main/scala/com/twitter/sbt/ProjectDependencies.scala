package com.twitter.sbt

/**
 * ProjectDependencies add explicit project dependencies
 * ala. adhoc-inlines, but it assumes that all projects in the project
 * dependency DAG are also ProjectDependency. This simplifies the
 * implementation and makes it more robust.
 */

import scala.collection.mutable.{HashSet, HashMap}

import java.util.Properties
import java.io.{FileInputStream, FileOutputStream, File, FileWriter, PrintWriter}
import pimpedversion._
import collection.jcl.Conversions._

import _root_.sbt._

trait ParentProjectDependencies
  extends BasicDependencyProject
  with ProjectCache
  with ManagedClasspathFilter
  with Environmentalist
  with GitHelpers
{
  if (this.isInstanceOf[AdhocInlines])
    throw new Exception("AdhocInlines are not compatible with ProjectDependencies")

  private def signature(prop: Properties): String = {
    val keys: Array[String] = convertSet(prop.stringPropertyNames()).toArray
    util.Sorting.quickSort(keys)
    keys map { key => "%s+%s".format(key, prop.getProperty(key)) } mkString
  }

  /**
   * Returns the projectClosure for the root project.
   */
  def rootProjectClosure: List[Project] = {
    info.parent match {
      case Some(parent) =>
        try {
          val m = parent.getClass.getMethod("rootProjectClosure")
          m.invoke(parent).asInstanceOf[List[Project]]
        } catch {
          case e =>
            log.error(
              ("Parent project of %s is not " +
               "a [Parent]ProjectDependencies project!").format(name))
            throw e
        }

      case None =>
        projectClosure
    }
  }

  /**
   * Flag management.
   */
  val ProjectDependenciesFile = ".has_project_deps_f0b6608e"
  private var _useProjectDependencies: Option[Boolean] = None
  private lazy val projectDependenciesFilePresent =
    Seq(Path.fromFile(ProjectDependenciesFile),
        Path.fromFile("..") / ProjectDependenciesFile).exists { _.exists }    

  def useProjectDependencies =
    _useProjectDependencies getOrElse {
      !environment.get("NO_PROJECT_DEPS").isDefined &&
      projectDependenciesFilePresent
    }

  private lazy val _projectDependencies = new HashSet[ProjectDependency]
  def getProjectDependencies = _projectDependencies

  def setUseProjectDependencies(which: Boolean): Boolean = {
    val old = useProjectDependencies
    _useProjectDependencies = Some(which)
    old
  }

  /**
   * Finds the the parent of this subproject, returns ``this'' if we
   * already are the parent project. This is needed to distinguish
   * "parent" projects vs. external dependencies in the DAG.
   */
  def subProjectParent: Project =
    info.parent match {
      case Some(parent: ParentProjectDependencies) =>
        if (parent.isSubProject(this)) parent else this
      case _ =>
        this
    }

  def projectSubProjects: Seq[Project] =
    info.parent match {
      case Some(parent: ParentProjectDependencies) =>
        parent.actualSubProjects
      case _ =>
        actualSubProjects
    }

  case class ProjectDependency(relPath: String, name: String) {
    def parentDependency: ProjectDependency =
      if (relPath == name) this
      else ProjectDependency(relPath, relPath)

    lazy val projectPath: Option[Path] = {
      val candidates = Seq(
        Path.fromFile(relPath),
        Path.fromFile("..") / relPath
      )

      candidates.find { path => (path / "project" / "build.properties").exists }
    }

    def resolveProject: Option[Project] = {
      projectCache("project:%s:%s".format(relPath, name)) {
        projectPath flatMap { projectPath =>
          val parentProject =
            projectCache("path:%s".format(projectPath)) { Some(project(projectPath)) }
          parentProject flatMap { parentProject => 
            if (parentProject.name != name) {
              // Try to find it in a subproject.
              parentProject.subProjects.find { _._2.name == name } map { _._2 }
            } else {
              Some(parentProject)
            }
          }
        }
      }
    }

    def resolveModuleID: Option[ModuleID] = {
      val project = subProjectParent
      val versionsPath =
        Path.fromFile(project.info.projectDirectory) / "project" / "versions.properties"

      val prop = new Properties
      prop.load(new FileInputStream(versionsPath.toString))

      val key = prop.getProperty("%s|%s".format(relPath, name))
      if (key ne null) {
        val Array(org, name) = key.split("/", 2)
        val version = prop.getProperty(key)
        if (version ne null)
          Some(org % name % version)
        else
          None
      } else {
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

  def isSubProject(p: Project) = super.subProjects.values contains p

  def actualSubProjects: Seq[Project] = super.subProjects.map(_._2).toSeq

  override def subProjects = {
    if (useProjectDependencies) {
      val projects = _projectDependencies flatMap { dep =>
        dep.resolveProject map { project =>
          dep.name -> project
        }
      }

      Map() ++ super.subProjects ++ projects
    } else {
      super.subProjects
    }
  }

  /**
   * TODO: always expose all library depencies? what about
   * chicken-and-egg re. versioning?  can we always query the
   * underlying module? [i think? it has to be released? as a fallback
   * we can use the current version-yes, do this.  and warn when it
   * happens.]
   */

  override def libraryDependencies =
    if (useProjectDependencies) {
      val missingProjectDependencies =
        _projectDependencies filter { !_.resolveProject.isDefined }
      super.libraryDependencies ++ {
        Set() ++ missingProjectDependencies flatMap { _.resolveModuleID }
      }
    } else {
      (Set() ++ _projectDependencies map { _.resolveModuleID.get }) ++ super.libraryDependencies
    }

  /**
   * Filters out dependencies that are in our DAG.
   */

  private def parentManagedDependencyFilter(config: Configuration, m: ModuleID): Boolean =
    info.parent match {
      case Some(parent) =>
        try {
          val method = parent.getClass.getMethod(
            "managedDependencyFilter",
            classOf[Configuration], classOf[ModuleID])
          method.invoke(parent, config, m).asInstanceOf[Boolean]
        } catch {
          case e =>
            log.error(
              ("Parent project of %s is not " +
               "a [Parent]ProjectDependencies project!").format(name))
            throw e
        }

      case None =>
        true
    }

  def filteredProjectClasspath(
    config: Configuration, projects: List[Project]
  ): PathFinder = fullUnmanagedClasspath(config) +++ {
    config match {
      case Configurations.Provided =>
        Path.emptyPathFinder
      case config =>
        filterPathFinderClasspath(managedClasspath(config)) { m =>
          !(projects exists { p => p.organization == m.organization && p.name == m.name })
        }
    }
  }

	override def fullClasspath(config: Configuration): PathFinder =
		if (!useProjectDependencies) {
      super.fullClasspath(config)
    } else {
      Path.lazyPathFinder {
        val set = new HashSet[Path]
		    for (project <- topologicalSort) {
          val method = project.getClass.getMethod(
						"filteredProjectClasspath",
            classOf[Configuration], classOf[List[Project]])

					val queryConfig =
            if (config == Configurations.Test &&
                (project ne this) && info.dependencies.forall(_ ne project)) {
              Configurations.Runtime
            } else {
              config
            }

          val projectClasspath =
						method.invoke(project, queryConfig, projectClosure).asInstanceOf[PathFinder]

					set ++= projectClasspath.get
		    }

		    set.toList
		  }
    }

  /**
   * Release management.
   */

  def lastReleasedVersion(): Option[Version] = {
    val project = subProjectParent
    val releasePropertiesPath =
      Path.fromFile(project.info.projectDirectory) / "project" / "release.properties"
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

  /**
   * Update versions for projectDependencies. We do so by querying our
   * dependencies for their currently released versions.
   */
  lazy val updateVersions = task {
    // TODO: use builderPath?
    val project = subProjectParent

    val versionsPath =
      Path.fromFile(project.info.projectDirectory) / "project" / "versions.properties"

    // Merge the existing one when it exists.
    val prop = new Properties
    if (versionsPath.exists)
      prop.load(new FileInputStream(versionsPath.toString))

    val oldSignature = signature(prop)

    val projects = _projectDependencies flatMap { dep => dep.resolveProject map { (_, dep) } }
    projects foreach { case (p, dep) =>
      val m = p.getClass.getMethod("lastReleasedVersion")
			val version = if (m ne null) {
				m.invoke(p).asInstanceOf[Option[Version]]
			} else {
				log.error("project %s is not a ReleaseManagement project".format(p.name))
				None
			}

      version foreach { version =>
				val key = "%s/%s".format(p.organization, p.name)
        prop.setProperty("%s|%s".format(dep.relPath, dep.name), key)
        prop.setProperty(key, version.toString)
			}
    }

    if (signature(prop) != oldSignature) {
      val stream = new FileOutputStream(versionsPath.toString)
      prop.store(stream,"Automatically generated by ProjectDependencies")
      stream.close()
      gitCommitFiles("updated versions.properties", versionsPath.toString)
    }

    None
  }

  lazy val testProject = task { args =>
    task {
      projectSubProjects foreach { _.call("test-only", args) }
      None
    }
  }

  /**
   * Utilities / debugging.
   */

  lazy val analyze = interactiveTask {
    val projectsAndDependencies = projectClosure map { project =>
      val dependencies = try {
        val m = project.getClass.getMethod("libraryDependencies")
        m.invoke(project).asInstanceOf[Set[ModuleID]]
      } catch { case _ => Set() }

      (project, dependencies)
    }

    projectsAndDependencies foreach { case (outerProject, dependencies) =>
      projectClosure foreach { innerProject =>
        dependencies foreach { dep =>
          if (innerProject.organization == dep.organization &&
              innerProject.name == dep.name) {
            log.warn(
              ("Project %s brings in dependency %s, but this is " +
               "also provided by project %s").format(outerProject, dep, innerProject))
          }
        }
      }
    }

    val dependencyToProject =
      projectsAndDependencies flatMap { case (project, dependencies) =>
        (dependencies map { (_, project) }).toList
      }

    val seenDeps =
      new scala.collection.mutable.HashMap[(String, String), List[(ModuleID, Project)]]

    dependencyToProject foreach { case (dep, project) =>
      val k = (dep.organization, dep.name)
      val l = seenDeps getOrElseUpdate(k, Nil: List[(ModuleID, Project)])

      seenDeps((dep.organization, dep.name)) = (dep, project) :: l
    }

    seenDeps foreach { case ((org, name), deps) =>
      val revisions = Set() ++ deps map { case (dep, _) => dep.revision }
      if (revisions.size > 1) {
        log.warn("Conflicting dependencies for %s:%s".format(org, name))
        revisions foreach { rev =>
          val projects =
            deps filter { case (dep, _) => dep.revision == rev } map { case (_, p) => p }
          val projectNames = projects map { _.name  }
          log.warn("On revision %s: %s".format(rev, projectNames mkString ","))
        }
      }
    }

    None
  }

  lazy val toggleProjectDependencies = task {
    _useProjectDependencies = Some(!useProjectDependencies)
    if (useProjectDependencies)
      log.info("project dependencies are on")
    else
      log.info("project dependencies are off")
    None
  }

  lazy val showParent = task {
    log.info("my name is: %s and my parent is: %s. my parentProject is: %s".format(
      name, info.parent, subProjectParent))
    None
  }

  lazy val showDependencies = task {
    log.info("Library dependencies:")
    libraryDependencies foreach { dep => log.info("  %s".format(dep)) }

    log.info("Project dependencies:")
    if (!useProjectDependencies)
      log.info("* project dependencies are currently turned off")
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

  lazy val showRootProjectClosure = task {
    log.info("Root project closure:")
    rootProjectClosure foreach { project =>
      println("  " + project + " " + project.hashCode)
    }

    None
  }
  
  lazy val showManagedClasspath = task { args =>
    val name = args match {
      case Array(configName) => configName
      case _ => "compile"
    }

    task {
      val config = Configurations.config(name)
      managedClasspath(config).get foreach { path =>
        println("> %s".format(path))
      }

      None
    }
  }

  // override def managedClasspath(config: Configuration) = {
  //   // println("MC: %s".format(config))
  //   super.managedClasspath(config)
  // }

	lazy val showMe = task {
    println(defaultConfigurationExtensions)
    None
  }

  lazy val showFullClasspath = task { args =>
    val name = args match {
      case Array(configName) => configName
      case _ => "compile"
    }

    task {
      val config = Configurations.config(name)
      fullClasspath(config).get foreach { path =>
        println("> %s".format(path.absolutePath))
      }

      None
    }
  }

  lazy val showLastReleasedVersion = task {
    println(lastReleasedVersion)
    None
  }

  lazy val showProjectPath = task {
    println(info.projectDirectory)
    None
  }
}

trait ProjectDependencies extends BasicScalaProject with ParentProjectDependencies {
  lazy val generateRunClasspath = task {
    val file = new File(info.projectDirectory, ".run_classpath")
		val out = new PrintWriter(new FileWriter(file))
    runClasspath.get foreach { path => out.println(path.absolutePath) }
    mainDependencies.scalaJars.get foreach { path => out.println(path.absolutePath) }
    out.close()
    None
  } dependsOn(compile, copyResources)

  lazy val showCompileClasspath = task {
    compileClasspath.get foreach { path =>
      println("> %s".format(path))
    }

    None
  }

  lazy val showOptionalClasspath = task {
    optionalClasspath.get foreach { path =>
			println("> %s".format(path))
		}
    None
  }

  lazy val showProvidedClasspath = task {
    providedClasspath.get foreach { path =>
			println("> %s".format(path))
		}
    None
  }

  lazy val showTestClasspath = task {
    testClasspath.get foreach { path =>
			println("> %s".format(path.absolutePath))
		}
    None
  }

  lazy val showRunClasspath = task {
    runClasspath.get foreach { path =>
			println("> %s".format(path.absolutePath))
		}
    None
  }

  lazy val showUnmanagedClasspath = task { args =>
    val name = args match {
      case Array(configName) => configName
      case _ => "compile"
    }

    task {
      val config = Configurations.config(name)
      fullUnmanagedClasspath(config).get foreach { path =>
        println("> %s".format(path))
      }

      None
    }
  }
}
