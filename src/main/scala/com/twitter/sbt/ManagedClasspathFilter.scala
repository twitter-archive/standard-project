package com.twitter.sbt

/**
 * ManagedClasspathFilter allows filtering of the classpath in terms
 * of packages. It maintains this mapping in a ".ivyjars" file in the
 * root (sub-)project directory.
 */

import java.io.File

import scala.collection.jcl.Conversions._

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.{ModuleDescriptor, DefaultModuleDescriptor}
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.resolve.IvyNode
import org.apache.ivy.core.LogOptions

import _root_.sbt._

trait ManagedClasspathFilter extends BasicManagedProject {
  /**
   * Define this to filter out dependencies.
   */
  def managedDependencyFilter(organization: String, name: String): Boolean

  case class IvyJar(organization: String, name: String, jar: String)

  // I'm submitting to this sbt antipattern here.  Lean into it. :-(
  lazy val ivyJars = {
    val source = io.Source.fromFile(new java.io.File(info.projectDirectory, ".ivyjars"))
    Map() ++ { source.getLines map { dirtyLine =>
      val line = dirtyLine.stripLineEnd
      // error out on parse error?
      val Array(organization, name, jar) = line.split("\t")
      (jar -> IvyJar(organization, name, jar))
    }}
  }

  override def inlineSettings = {
    val filteredDeps = super.libraryDependencies.filter { m =>
      managedDependencyFilter(m.organization, m.name)
    }

    new InlineConfiguration(
      projectID, filteredDeps, ivyXML, ivyConfigurations,
      defaultConfiguration, ivyScala, ivyValidate)
  }

  override def managedClasspath(config: Configuration): PathFinder =
    super.managedClasspath(config) filter { path =>
			ivyJars.get(path.name) match {
				case Some(IvyJar(organization, name, _)) =>
          managedDependencyFilter(organization, name)

				case None =>
					// Exclude stuff we don't know about.
					log.warn("ManagedClasspathFilter: %s NOT FOUND, excluding".format(path.name))
					false
			}
		}

  /**
   * Ivy resolution / updating.
   */

  private def resolve(logging: UpdateLogging.Value)(
    ivy: Ivy,
    module: DefaultModuleDescriptor,
    defaultConf: String
  ) = {
		val resolveOptions = new ResolveOptions
		resolveOptions.setLog(ivyLogLevel(logging))
		val resolveReport = ivy.resolve(module, resolveOptions)
		if (resolveReport.hasError) {
			throw new ResolveException(
        resolveReport.getAllProblemMessages.toArray.map(_.toString).toList.removeDuplicates)
    }

    val file = new File(info.projectDirectory, ".ivyjars")
		val out = new java.io.PrintWriter(new java.io.FileWriter(file))

		// println("RESOLVE")
    // for (dep <- resolveReport.getDependencies; id = dep.asInstanceOf[IvyNode].getId) {
    //   println("ID %s".format(id))
    // }
    
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

  override def updateTask(
    module: => IvySbt#Module,
    configuration: => UpdateConfiguration
  ) = ivyTask { update(module, configuration) }

}
