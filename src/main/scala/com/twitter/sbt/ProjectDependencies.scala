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
  with GitHelpers
{
  private def signature(prop: Properties): String = {
    val keys: Array[String] = convertSet(prop.stringPropertyNames()).toArray
    util.Sorting.quickSort(keys)
    keys map { key => "%s+%s".format(key, prop.getProperty(key)) } mkString
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

  protected def withProjectDependenciesOff[A](f: () => A): A = {
    val old = setUseProjectDependencies(false)
    val oldProjects =
      super.subProjects
        .filter { _.isInstanceOf[ProjectDependencies] }
        .map    { _.asInstanceOf[ProjectDependencies] }
        .map    { p => (p, p.setUseProjectDependencies(false)) }

    println("WITH OFF %s".format(oldProjects.mkString(", ")))

    val result = f()

    setUseProjectDependencies(old)
    oldProjects foreach { case (p, old) => p.setUseProjectDependencies(old) }
    result
  }

  private var isParentProject = true
  def setIsSubProject() {
    isParentProject = false
  }

  override def shouldCheckOutputDirectories = false

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

    def resolveModuleID: Option[ModuleID] = {
      val project = info.parent getOrElse ProjectDependencies.this
      val versionsPath =
        Path.fromFile(project.info.projectPath.absolutePath) / "project" / "versions.properties"

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

  private[this] var subProjectsInitialized = false

  override def subProjects = {
    if (!subProjectsInitialized) {
      super.subProjects foreach { case (_, p) =>
        p.asInstanceOf[ProjectDependencies].setIsSubProject()
        setProjectCacheStoreInProject(p, projectCacheStore)
      }
      subProjectsInitialized = true
    }

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

  override def libraryDependencies =
    if (useProjectDependencies) {
      val missingProjectDependencies =
        _projectDependencies filter { !_.resolveProject.isDefined }
      super.libraryDependencies ++ (
        Set() ++ missingProjectDependencies flatMap { _.resolveModuleID }
      )
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
    val project = if (isParentProject) this else info.parent.get
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
    // TODO: use builderPath?
    val project = if (isParentProject) this else info.parent.get

    val versionsPath =
      Path.fromFile(project.info.projectPath.absolutePath) / "project" / "versions.properties"

    // Merge the existing one when it exists.
    val prop = new Properties
    if (versionsPath.exists)
      prop.load(new FileInputStream(versionsPath.toString))

    val oldSignature = signature(prop)

    val projects = _projectDependencies flatMap { dep => dep.resolveProject map { (_, dep) } }
    projects foreach { case (p, dep) =>
      val m = p.getClass.getDeclaredMethod("lastReleasedVersion")
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

  // Unpossible. :-(

	// // projectIntransitiveActions
  // override def act(name: String): Option[String] = {
  //   println("ACT %s".format(name))
  //   val r = if (name == "publish-local") {
  //     println("TURNING OFF project deps")
  //     withProjectDependenciesOff { () =>
  //       super.act(name)
  //     }
  //   } else
  //     super.act(name)
  //  
  //   println("DONE!")
  //   r
  // }

  // override lazy val publish = task { None }

	// override def publishTask(
  //   module: => IvySbt#Module,
  //   publishConfiguration: => PublishConfiguration
  // ) = task {
  //   println("PUBLISH TASK!!!")
  //   withProjectDependenciesOff {
  //     println("running with OFF...")
  //     val r = super.publishTask(module, publishConfiguration).run
  //     println("~~~~~ running with OFF...")
  //     r
  //   }
  // }

  /**
   * Utilities / debugging.
   */

  lazy val fooBar = task {
    super.subProjects foreach { sp =>
      println("SUBPROJECT %s".format(sp))
      println("SUBPROJECT %s".format(sp.isInstanceOf[ProjectDependencies]))
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
    log.info("my name is: %s and my parent is: %s. my parent project status is: %s".format(
      name, info.parent, isParentProject))
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

  lazy val showProjectPath = task {
    println(info.projectPath.absolutePath)
    None
  }
}
