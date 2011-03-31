package com.twitter.sbt

import scala.collection.jcl
import scala.collection.jcl.Conversions._
import scala.collection.mutable.{HashSet, HashMap}

import java.io.File

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.{ModuleDescriptor, DefaultModuleDescriptor}
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.resolve.IvyNode
import org.apache.ivy.core.LogOptions

// TODO: check versions, display discrepancies, etc.
// TODO: check that it's an actual sbt project there, otherwise skip (xrayspecs)
// TODO: check versions
// TODO: show whole project graph

import _root_.sbt._


// object RawProjectCache {
//   private[this] val cache = new HashMap[String, Project]
//   def apply(path: Path)(make: => Project) = cache.getOrElseUpdate(path.absolutePath, make)
// }

object inline {
  // TODO: push as much of this into per-project caches as
  // possible. we need the global register to filter the classpath--
  // but not much else?  we can determinstically recreate these per
  // project.

  sealed abstract class ResolvedLibraryDependency
  case class ModuleDependency(m: ModuleID) extends ResolvedLibraryDependency
  case class InlineDependency(m: ModuleID, project: Project) extends ResolvedLibraryDependency

  case class ModuleDescriptor(organization: String, name: String)

  val noInlined = new HashSet[ModuleDescriptor]
  val inlined   = new HashSet[ModuleDescriptor]
}

trait AdhocInlines extends BasicManagedProject with Environmentalist {
  class ProjectCache {
    private[this] var cache = new HashMap[String, Project]
   
    def getStore() = cache
   
    def setStore(underlying: HashMap[String, Project]) {
      cache = underlying
    }
   
    def apply(key: String)(make: => Option[Project]) = {
      cache.get(key) match {
        case someProject@Some(_) => someProject
        case None =>
          val made = make
          made foreach { project =>
            cache(key) = project
          }
   
          made
      }
    }  
  }

  private[this] lazy val relPaths = new HashMap[(String, String), String]
  private[this] lazy val unpublishedInlines = new HashSet[(String, String)]
  private[this] lazy val explicitDependencies = new HashSet[Dependency]
  private[this] lazy val projectCache = new ProjectCache

  def setProjectCacheStore(store: HashMap[String, Project]) {
    println("SETTING PROJECT CACHE STORE (%d)".format(store.size))
    // (merge?)
    println("OLD ONE (%d)".format(projectCache.getStore.size))

    projectCache.setStore(store)
    subProjects foreach {
      case (name, adhoc: AdhocInlines) =>
        println(":-) %s".format(name))
        adhoc.setProjectCacheStore(store)
      case (name, _) =>
        println(":-(%s)".format(name))
    }
  }

  // For future use:
  val projectIsInlined = true

  // TODO: make a registry for these changes.
  class RichModuleID(m: ModuleID) {
    // This is side effecting. Nasty, but it gets the job done.
    def relativePath(name: String) = {
      relPaths((m.organization, m.name)) = name
      m
    }

    def withUnpublished() = {
      unpublishedInlines += Pair(m.organization, m.name)
      m
    }

    def noInline() = {
      // TODO: should noInlines really be global?
      inline.noInlined += inline.ModuleDescriptor(m.organization, m.name)
      m
    }
  }

  case class Dependency(relPath: String, name: String) {
    // TODO: do a better search? 
    def projectPath: Path = Path.fromFile("..") / relPath

    def resolveProject: Option[Project] = {
      projectCache("project:%s".format(name)) {
        println("PROJECT CACHE MAKE MAKE: %s".format(name))
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

        foundProject map { wrapProject(_) }
      }
    }
  }

  implicit def moduleIDToRichModuleID(m: ModuleID) = new RichModuleID(m)
  implicit def stringToAlmostDependency(relPath: String) = new {
    def ~(name: String) = Dependency(relPath, name)
  }

  implicit def stringToDependency(relPath: String) =
    Dependency(relPath, relPath)

  override def shouldCheckOutputDirectories = false

  def isInlining = environment.get("SBT_ADHOC_INLINE").isDefined
  def inlineSearchPath = environment.getOrElse("SBT_ADHOC_INLINE_PATH", "..")

  def dependencies(deps: Dependency*) {
    explicitDependencies ++= deps
  }

  private def resolveProject(organization: String, name: String, path: Path) =
    projectCache("project:%s/%s".format(organization, name)) {
      if ((path / "project" / "build.properties").exists) {
        val rawProject = projectCache("path:%s".format(path)) { Some(project(path)) }
        val foundProject = rawProject flatMap { rawProject =>
          if (rawProject.name != name) {
            // Try to find it in a subproject.
            rawProject.subProjects.find(_._2.name == name) map (_._2)
          } else {
            Some(rawProject)
          }
        }

        if (!foundProject.isDefined) {
          log.warn("project name mismatch @ %s (expected: %s got: %s)".format(
            path, name, rawProject.get.name))
          None
        } else if (foundProject.get.organization != organization) {
          log.warn("project organization mismatch @ %s (expected: %s got: %s)".format(
            path, organization, foundProject.get.organization))
          None
        } else {
          Some(wrapProject(foundProject.get))
        }
      } else {
        log.warn("invalid sbt project @ %s".format(path))
        None
      }
    }


  // println("project cache", info.projectDirectory, ProjectCache)

  // We use ``info.projectDirectory'' instead of ``name'' here because
  // for subprojects, names aren't initialized as of this point
  // [seemingly not before the constructor has finished running].
  // if (isInlining) {
  //   log.info("ad-hoc inlines enabled for " + info.projectDirectory)
  // } else {
  //   log.info(
  //     ("ad-hoc inlines NOT currently enabled " +
  //      "for %s set SBT_ADHOC_INLINE=1 to enable")
  //     .format(info.projectDirectory))
  // }

  if (environment.get("SBT_INLINE").isDefined) {
    log.error("ad-hoc inlines are incompatible with SBT_INLINE")
    System.exit(1)
  }

  case class IvyJar(organization: String, name: String, jar: String)
  // I'm submitting to this sbt antipattern here.  Lean into it.
  lazy val ivyJars = {
    val source = io.Source.fromFile(new java.io.File(info.projectDirectory, ".ivyjars"))
    Map() ++ { source.getLines map { dirtyLine =>
      val line = dirtyLine.stripLineEnd
      // error out on parse error?
      val Array(organization, name, jar) = line.split("\t")
      (jar -> IvyJar(organization, name, jar))
    }}
  }

  def resolvedPaths(relPath: String) = {
    inlineSearchPath.split(":").map{ file => Path.fromFile(file) / relPath }.filter(_.isDirectory)
  }

  private[this] lazy val resolvedLibraryDependencies =
    if (!isInlining) {
      super.libraryDependencies map inline.ModuleDependency
    } else {
      super.libraryDependencies map { module =>
        val descriptor = inline.ModuleDescriptor(module.organization, module.name)
        if (inline.noInlined contains descriptor) {
          inline.ModuleDependency(module)
        } else {
          val relPath =
            relPaths.get((module.organization, module.name)).getOrElse(module.name)

          resolvedPaths(relPath).firstOption match {
            case Some(path) =>
              resolveProject(module.organization, module.name, path) match {
                case Some(project) =>
                  // XXX: we can't do this test here because of sbt
                  // architecture idiocy. in the cascade of lazily
                  // initiated values that have side effects, querying
                  // ``version'' at this juncture attempts retrieving
                  // a hitherto undefined property when using
                  // subprojects (those without their own
                  // ``build.properties'' files). Sigh. Sigh. Sigh.

                  // if (project.version.toString != module.revision) {
                  //   log.warn("version mismatch for %s (%s is inlined, %s is requested)".format(
                  //     module.name, project.version.toString, module.revision))
                  // }

                  inline.inlined += descriptor
                  inline.InlineDependency(module, project)

                case None =>
                  inline.ModuleDependency(module)
              }
            case None =>
              inline.ModuleDependency(module)
          }
        }
      }
    }

  private[this] lazy val moduleDependencies =
    resolvedLibraryDependencies.flatMap {
      case inline.ModuleDependency(module) => Some(module)
      case _ => None
    }

  private[this] lazy val inlineDependencies =
    resolvedLibraryDependencies.flatMap {
      case inline.InlineDependency(module, project) => Some((module, project))
      case _ => None
    }

  override def libraryDependencies = Set() ++ moduleDependencies

  lazy val showClasspath = task {
    log.info(fullClasspath(Configurations.Compile).toString)
    None
  }

  lazy val showLibraryDependencies = task {
    log.info("Inlined dependencies:")
    inlineDependencies foreach { case (m, project) =>
      log.info("  %s @ %s".format(m, project.info.projectPath))
    }

    log.info("Library dependencies:")
    moduleDependencies foreach { m => log.info("  %s".format(m)) }

    log.info("Explicit dependencies:")
    explicitDependencies foreach { dep => log.info("  %s".format(dep)) }

    None
  }

  lazy val showProjectClosure = task {
    log.info("Project closure:")
    projectClosure foreach { project =>
      println("  " + project + " " + project.hashCode)
    }
    None
  }

  private def wrapProject(p: Project) = {
    println("WRAPPING PROJECT OF CLASS", p.getClass)
    // p.getClass.getDeclaredMethods foreach { method =>
    //   println("METHOD", method)
    // }
    val m = p.getClass.getDeclaredMethod(
      "setProjectCacheStore", classOf[HashMap[String, Project]])
    if (m ne null) {
			m.invoke(p, projectCache.getStore)
		} else {
			println("whining loudly.")
		}
    
    p
  }


  // p match {
  //   case _: AdhocInlines => p
  //   case p: DefaultProject => new WrappedDefaultProject(p) with AdhocInlines
  //   case p => p
  // }

  // Use the full set of dependencies (super.libraryDependencies) for
  // module updates.
  override def inlineSettings = {
    val filteredDeps = super.libraryDependencies.filter { m =>
      !(unpublishedInlines contains (m.organization, m.name))
    }

    new InlineConfiguration(projectID, filteredDeps, ivyXML, ivyConfigurations,
      defaultConfiguration, ivyScala, ivyValidate)
  }

  private def resolve(logging: UpdateLogging.Value)(
    ivy: Ivy, module: DefaultModuleDescriptor,
    defaultConf: String) =
	{
		val resolveOptions = new ResolveOptions
		resolveOptions.setLog(ivyLogLevel(logging))
		val resolveReport = ivy.resolve(module, resolveOptions)
		if(resolveReport.hasError) {
			throw new ResolveException(
        resolveReport.getAllProblemMessages.toArray.map(_.toString).toList.removeDuplicates)
    }

    val file = new File(info.projectDirectory, ".ivyjars")
		val out = new java.io.PrintWriter(new java.io.FileWriter(file))

		println("RESOLVE")
    for (dep <- resolveReport.getDependencies; id = dep.asInstanceOf[IvyNode].getId) {
      println("ID %s".format(id))
    }
    
    for (dep <- resolveReport.getDependencies; id = dep.asInstanceOf[IvyNode].getId) {
			out.println("%s\t%s\t%s-%s.jar".format(
        id.getOrganisation, id.getName,
        id.getName, id.getRevision))
    }

		out.close()
	}

	private def update(module: IvySbt#Module, configuration: UpdateConfiguration) {
		module.withModule { case (ivy, md, default) =>
			import configuration._
			resolve(logging)(ivy, md, default)
			val retrieveOptions = new RetrieveOptions
			retrieveOptions.setSync(synchronize)

			val patternBase = retrieveDirectory.getAbsolutePath
			val pattern =
				if(patternBase.endsWith(File.separator))
					patternBase + configuration.outputPattern
				else
					patternBase + File.separatorChar + configuration.outputPattern

      ivy.retrieve(md.getModuleRevisionId, pattern, retrieveOptions)
		}
	}

	import UpdateLogging.{Quiet, Full, DownloadOnly}
	import LogOptions.{LOG_QUIET, LOG_DEFAULT, LOG_DOWNLOAD_ONLY}
	private def ivyLogLevel(level: UpdateLogging.Value) =
		level match {
			case Quiet => LOG_QUIET
			case DownloadOnly => LOG_DOWNLOAD_ONLY
			case Full => LOG_DEFAULT
		}

  override def updateTask(module: => IvySbt#Module, configuration: => UpdateConfiguration) = {
	  ivyTask { update(module, configuration) }
  }

  private lazy val wtf: Boolean = {
    explicitDependencies foreach { dep =>
      // XXX com.twitter
      inline.inlined += inline.ModuleDescriptor("com.twitter", dep.name)
    }

    super.subProjects foreach { case (_, p) => wrapProject(p) }

    true
  }

  override def subProjects = {
    require(wtf)

    val mapped =
      inlineDependencies map { case (m, project) =>
        m.name -> project
      }

    val explicit =
      explicitDependencies map { dep =>
        val project = dep.resolveProject
        if (!project.isDefined) {
          log.error("could not find dependency %s".format(dep))
          System.exit(1)
        }

        dep.name -> project.get
      }

    Map() ++ super.subProjects ++ mapped ++ explicit
  }

  override def managedClasspath(config: Configuration): PathFinder =
    if (isInlining) {
      super.managedClasspath(config) filter { path =>
        ivyJars.get(path.name) match {
          case Some(IvyJar(organization, name, jar)) =>
            !(inline.inlined contains inline.ModuleDescriptor(organization, name))

          case None =>
            // Exclude stuff we don't know about.
            // log.warn("%s NOT FOUND".format(path.name)) error out?
            false
        }
      }
    } else {
      super.managedClasspath(config)
    }
}
