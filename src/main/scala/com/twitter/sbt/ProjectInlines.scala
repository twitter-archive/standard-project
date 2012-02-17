package com.twitter.sbt

import sbt._
import Keys._

import sbt._
import Keys._
import com.twitter.sbt._
import com.twitter.sbt.SubversionPublisher._

/**
 * capture an SBT ModuleID along with a git URL, subproject within that repo,
 * and an optional override for where it should be checked out
 */
case class InlineableModuleID(moduleId: Option[ModuleID],
                              gitURI: Option[String] = None,
                              projName: Option[String] = None,
                              dir: Option[File] = None,
                              subproj: Option[String] = None,
                              softLink: Option[String] = None,
                              dirOverride: Option[File] = None) {

  def or(subproj: String) = copy(subproj = Some(subproj))
  /**
   * convenience method for specifying a subproject
   */
  def in(dir: File) = copy(dir = Some(dir), softLink = Some(dir.getName), projName = Some(dir.getName))
  /**
   * convenience method for attaching an scm uri
   */
  def at(git: String) = {
    val newDir: Option[File] = dir match {
      case Some(d) => Some(d)
      case None => Some(file("..") / git.split("/").last.split("\\.").dropRight(1).mkString("."))
    }
    val newSoftLink = softLink match {
      case Some(s) => Some(s)
      case None => Some(newDir.get.getName)
    }
    copy(gitURI = Some(git), dir = newDir, softLink = newSoftLink)
  }
}

/**
 * from coderspiel (http://code.technically.us/post/9545154150/local-external-projects-in-sbt) more or less
 */
class Inlines (inlines: InlineableModuleID*) {
  /**
   * scan through inlines, returning ProjectRefs for
   * those projects we have softlinks for
   */
  def addDeps (p: Project): Project = (inlines.flatMap { inline =>
    inline.softLink flatMap { softLink =>
      if (file(softLink).exists) {
        Some(symproj(inline.dir.get, inline.subproj))
      } else {
        None
      }
    }
  }).foldLeft(p) { _ dependsOn _ }
  /**
   * scan through inlines, returning ModuleIDs for
   * those projects we do not have softlinks for
   */
  def libDeps: Seq[ModuleID] = (inlines.flatMap { inline =>
    inline.softLink.flatMap { softLink =>
      if (!file(softLink).exists) {
        Some(inline.moduleId)
      } else {
        None
      }
    }
  }).flatten

  /**
   * create a projectref from a file and subproj
   */
  private def symproj (dir: File, subproj: Option[String] = None) = {
    subproj match {
      case Some(sub) => ProjectRef(dir, sub)
      case _ => RootProject(dir)
    }
  }
}

/**
 * settings, implicit from moduleid -> inlineablemoduleid, etc.
 */
object InlinesPlugin extends Plugin {
  /**
   * settingkey to record our inlineablemoduleids
   */
  val inlines = SettingKey[Seq[InlineableModuleID]]("project-inlines")

  def makeDepDir(state: State, projName: String, dir: File, inlineable: InlineableModuleID): Boolean = {
    if (dir.exists) {
      true
    } else if (!dir.exists && inlineable.gitURI.isDefined) {
      "git clone %s %s".format(inlineable.gitURI.get, file(dir.getCanonicalPath)) !

      true
    } else {
      State.stateOps(state).log.error("%s has no SCM link defined, cannot create directory %s".format(projName, dir))
      false
    }
  }

  /**
   * check to see if a project is cloned (if not, clone it)
   * create a softlink to current directory, reload state
   */
  def inline = Command.single("inline") { (state: State, v: String) => {
    val extracted = Project.extract(state)
    val inlineables = extracted.get(InlinesPlugin.inlines)
    inlineables.find(_.softLink == Some(v)) match {
      case Some(inlineable) => {
        val stateOpt = for (dir <- inlineable.dir;
                            softLink <- inlineable.softLink) yield {
          if (makeDepDir(state, v, dir, inlineable)) {
            val softLinkTarget = file(softLink)
            if (!softLinkTarget.exists) {
              "ln -s %s ./%s".format(dir.getCanonicalPath, softLink) !
            }
            State.stateOps(state).reload
          } else {
            state.fail
          }
        }
        stateOpt.getOrElse(state.fail)
      }
      case _ => {
        State.stateOps(state).log.error("couldn't find project %s to inline".format(v))
        State.stateOps(state).reload
      }
    }
  }}

  /**
   * remove softlink, reload project state
   */
  def outline = Command.single("outline") { (state: State, v: String) => {
    "rm -f ./%s".format(v, v) !

    State.stateOps(state).reload
  }}

  val newSettings = Seq(
    inlines := Seq(),
    commands ++= Seq(inline, outline)
  )
}

object Inlines {
  /**
   * utility to build Inlines from an array of InlineableModuleIDs
   */
  def apply(inlines: InlineableModuleID*) = new Inlines(inlines:_*)
  /**
   * implicit from ModuleID to InlineableModuleID
   */
  implicit def moduleIdToInline(moduleId: ModuleID): InlineableModuleID = InlineableModuleID(Some(moduleId))
}

/**
 * Project utility for InlinedProject construction
 */
object InlinedProject {

  /**
   * build a new project with optional inlines
   */
  def apply(id: String, base: File,
            aggregate: => Seq[ProjectReference] = Nil,
            dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
            delegates: => Seq[ProjectReference] = Nil,
	    settings: => Seq[Setting[_]] = Defaults.defaultSettings,
            configurations: Seq[Configuration] = Configurations.default,
            inlines: Seq[InlineableModuleID]): Project = {
    val inliners = Inlines(inlines:_*)
    inliners.addDeps(Project(id, base, aggregate, dependencies, delegates, settings, configurations))
    .settings(
      libraryDependencies ++= inliners.libDeps,
      InlinesPlugin.inlines := inlines)
  }
}
