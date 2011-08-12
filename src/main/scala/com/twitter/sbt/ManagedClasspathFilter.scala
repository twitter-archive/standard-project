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
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.plugins.report.XmlReportParser
import org.apache.ivy.core.resolve.ResolveOptions
        
import _root_.sbt._

trait ManagedClasspathFilter extends BasicManagedProject {
  // I'm submitting to this sbt antipattern here.  Lean into it. :-(
  lazy val ivyJars: Map[String, ModuleID] = {
    val source = io.Source.fromFile(new java.io.File(info.projectDirectory, ".ivyjars"))
    Map() ++ { source.getLines map { dirtyLine =>
      val line = dirtyLine.stripLineEnd
      val Array(path, organization, name, revision) = line.split("\t")
      (path -> ModuleID(organization, name, revision))
    }}
  }

  def filterPathFinderClasspath(finder: PathFinder)(f: (ModuleID => Boolean)): PathFinder =
    finder.filter { path =>
      ivyJars.get(path.absolutePath) match {
        case Some(m) => f(m)
        case None =>
          // Exclude stuff we don't know about.
          log.warn("ManagedClasspathFilter: %s NOT FOUND in .ivyjars, excluding".format(path))
          false
      }
    }

  /**
   * Ivy resolution / updating.
   */

  private def resolve(
    logging: UpdateLogging.Value,
    ivy: Ivy,
    module: DefaultModuleDescriptor
  ) = {
    val resolveOptions = new ResolveOptions
    resolveOptions.setLog(ivyLogLevel(logging))
    val resolveReport = ivy.resolve(module, resolveOptions)
    if (resolveReport.hasError) {
      throw new ResolveException(
      resolveReport.getAllProblemMessages.toArray.map(_.toString).toList.removeDuplicates)
    }

    resolveReport
  }

  private def update(module: IvySbt#Module, configuration: UpdateConfiguration) {
    module.withModule { case (ivy, md, default) =>
      import configuration._
      val report = resolve(logging, ivy, md)
      val retrieveOptions = new RetrieveOptions
      retrieveOptions.setSync(synchronize)

      val patternBase = retrieveDirectory.getAbsolutePath
      val pattern =
        if (patternBase.endsWith(File.separator))
          patternBase + configuration.outputPattern
        else
          patternBase + File.separatorChar + configuration.outputPattern

      val mrid = md.getModuleRevisionId
      ivy.retrieve(mrid, pattern, retrieveOptions)
    
      /**
       * Report on the retrieve.
       */
  
      val settings = ivy.getSettings
      val cacheManager = settings.getResolutionCacheManager
      val configs = md.getConfigurationsNames()
      
      type ArtifactMap = java.util.Map[ArtifactDownloadReport, java.util.Set[String]]
  
      val artifactsToCopy = ivy.getRetrieveEngine.determineArtifactsToCopy(
        mrid, pattern, retrieveOptions).asInstanceOf[ArtifactMap]
  
      val file = new File(info.projectDirectory, ".ivyjars")
      val out = new java.io.PrintWriter(new java.io.FileWriter(file))
  
      artifactsToCopy foreach { case (report, paths) =>
        val artifact = report.getArtifact
        val artifactMrid = artifact.getModuleRevisionId
        paths foreach { path =>
          out.println("%s\t%s\t%s\t%s".format(
            path, artifactMrid.getOrganisation,
            artifactMrid.getName, artifactMrid.getRevision))
        }
      }
  
      out.close()
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
  
  lazy val showUpdateModule = task {
    updateIvyModule.withModule { case (_, md, _) =>
      println(md)
    }
    None
  }
}
