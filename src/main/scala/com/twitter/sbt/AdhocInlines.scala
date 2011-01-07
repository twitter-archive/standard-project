package com.twitter.sbt

import scala.collection.jcl
import scala.collection.jcl.Conversions._
import scala.collection.mutable.{HashSet, HashMap}

import java.io.File

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.{
  ModuleDescriptor, DefaultModuleDescriptor}
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.resolve.IvyNode
import org.apache.ivy.core.LogOptions

// TODO: check versions, display discrepancies, etc.
// TODO: check that it's an actual sbt project there, otherwise skip (xrayspecs)
// TODO: check versions

import _root_.sbt._

object ProjectCache {
  private[this] val cache = new HashMap[(String, String), Project]

  def apply(organization: String, name: String)(make: => Option[Project]) = {
    val key = (organization, name)
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

object RawProjectCache {
  private[this] val cache = new HashMap[String, Project]
  def apply(path: Path)(make: => Project) = cache.getOrElseUpdate(path.absolutePath, make)
}


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
  private[this] lazy val relPaths = new HashMap[(String, String), String]

  // TODO: make a registry for these changes.
  class RichModuleID(m: ModuleID) {
    // This is side effecting. Nasty, but it gets the job done.
    def relativePath(name: String) = {
      relPaths((m.organization, m.name)) = name
      m
    }
  }

  implicit def moduleIDToRichModuleID(m: ModuleID) = new RichModuleID(m)
  override def shouldCheckOutputDirectories = false

  private def resolveProject(organization: String, name: String, path: Path) =
    ProjectCache(organization, name) {
      if ((path / "project" / "build.properties").exists) {
        val rawProject = RawProjectCache(path) { project(path) }
        val foundProject =
          if (rawProject.name != name) {
            // Try to find it in a subproject.
            rawProject.subProjects.find(_._2.name == name) map (_._2)
          } else {
            Some(rawProject)
          }

        if (!foundProject.isDefined) {
          log.warn("project name mismatch @ %s (expected: %s got: %s)".format(
            path, name, rawProject.name))
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

  val isInlining = environment.get("SBT_ADHOC_INLINE").isDefined

  // We use ``info.projectDirectory'' instead of ``name'' here because
  // for subprojects, names aren't initialized as of this point
  // [seemingly not before the constructor has finished running].
  if (isInlining) {
    log.info("ad-hoc inlines enabled for " + info.projectDirectory)
  } else {
    log.info(
      ("ad-hoc inlines NOT currently enabled " +
       "for %s set SBT_ADHOC_INLINE=1 to enable")
      .format(info.projectDirectory))
  }

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
    val search_path = environment.getOrElse("SBT_ADHOC_INLINE_PATH", "..")
    search_path.split(":").map{ file => Path.fromFile(file) / relPath }.filter(_.isDirectory)
  }

  // Yikes.  this stuff is pretty nasty.
  lazy val resolvedLibraryDependencies = {
    super.libraryDependencies map { module =>
      // We only need to do relPath here, because resolveProject()
      // will search subProjects too.
      val relPath = relPaths.get((module.organization, module.name)).getOrElse(module.name)
      val pathOption = resolvedPaths(relPath).firstOption

      val descriptor = inline.ModuleDescriptor(module.organization, module.name)
      if (!isInlining) {
        inline.ModuleDependency(module)
      } else if (inline.noInlined contains descriptor) {
        inline.ModuleDependency(module)
      } else if (pathOption.isDefined) {
        val path = pathOption.get
        resolveProject(module.organization, module.name, path) match {
          case Some(project) =>
            // TODO: use logging.
            if (project.version.toString != module.revision) {
              log.warn("version mismatch for %s (%s is inlined, %s is requested)".format(
                module.name, project.version.toString, module.revision))
            }

            inline.inlined += descriptor
            inline.InlineDependency(module, project)

          case None =>
            inline.ModuleDependency(module)
        }
      } else {
        inline.ModuleDependency(module)
      }
    }
  }

  lazy val moduleDependencies =
    resolvedLibraryDependencies.filter(_.isInstanceOf[inline.ModuleDependency])
  lazy val inlineDependencies =
    resolvedLibraryDependencies.filter(_.isInstanceOf[inline.InlineDependency])

  lazy val showClasspath = task {
    log.info(fullClasspath(Configurations.Compile).toString)
    None
  }

  lazy val showLibraryDependencies = task {
    log.info("Inlined dependencies:")
    inlineDependencies foreach { case inline.InlineDependency(m, project) =>
      log.info("  %s @ %s".format(m, project.info.projectPath))
    }

    log.info("Library dependencies:")
    moduleDependencies foreach { case inline.ModuleDependency(m) =>
      log.info("  %s".format(m))
    }

    None
  }

  private def wrapProject(p: Project) =
    p match {
      case p: DefaultProject => new WrappedDefaultProject(p) with AdhocInlines
      case p => p
    }

	private def resolve(logging: UpdateLogging.Value)(
    ivy: Ivy, module: DefaultModuleDescriptor,
    defaultConf: String) =
	{
		val resolveOptions = new ResolveOptions
		resolveOptions.setLog(LogOptions.LOG_QUIET /*LOG_DEFAULT*/)
		val resolveReport = ivy.resolve(module, resolveOptions)
		if(resolveReport.hasError) {
			throw new ResolveException(
        resolveReport.getAllProblemMessages.toArray.map(_.toString).toList.removeDuplicates)
    }

    val file = new File(info.projectDirectory, ".ivyjars")
		val out = new java.io.PrintWriter(new java.io.FileWriter(file))

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

	override def updateTask(module: => IvySbt#Module, configuration: => UpdateConfiguration) =
		ivyTask { update(module, configuration) }

  override def subProjects = {
    val mapped =
      inlineDependencies map { case inline.InlineDependency(m, project) =>
        m.name -> project
      }

    Map() ++ super.subProjects ++ mapped
  }

  // To make sure we publish the correct POMs, and always have the
  // // jars at our avail.
  // override def inlineSettings = {
  //   val parent = super.inlineSettings
  //   val inlinedDeps = inlineDependencies map { case inline.InlineDependency(m, _) => m }

  //   new InlineConfiguration(
  //     parent.module, parent.dependencies ++ inlinedDeps,
  //     parent.ivyXML, parent.configurations, parent.defaultConfiguration,
  //     parent.ivyScala, parent.validate)
  // }

  // do we want this? maybe?

  // override def libraryDependencies =
  //   moduleDependencies map { case inline.ModuleDependency(m) => m }

  // only change run classpath?

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
