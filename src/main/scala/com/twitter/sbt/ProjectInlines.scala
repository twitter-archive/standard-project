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
case class InlineableModuleID(moduleId: ModuleID,
                              scm: String,
                              subproj: Option[String] = None,
                              dirOverride: Option[File] = None) {
  /**
   * convenience method for specifying a subproject
   */
  def /(sub: String) = copy(subproj = Some(sub))
  /**
   * convenience method for attaching an scm uri
   */
  def at(scm: String) = copy(scm = scm)
  /**
   * where we should clone the repo
   */
  def dir: File = {
    dirOverride match {
      case Some(d) => d
      case _ => file("..") / scm.split("/").last.split("\\.").dropRight(1).mkString(".")
    }
  }
  /**
   * marker dir
   */
  def softLink: String = dir.getName
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
    if (file(inline.softLink).exists) {
      Some(symproj(file(inline.softLink), inline.subproj))
    } else {
      None
    }
  }).foldLeft(p) { _ dependsOn _ }
  /**
   * scan through inlines, returning ModuleIDs for
   * those projects we do not have softlinks for
   */
  def libDeps: Seq[ModuleID] = inlines.flatMap { inline =>
    if (!file(inline.softLink).exists) {
      Some(inline.moduleId)
    } else {
      None
    }
  }
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
object Inlines extends Plugin {
  /**
   * settingkey to record our inlineablemoduleids
   */
  val inlines = SettingKey[Seq[InlineableModuleID]]("inlines")
  /**
   * utility to build Inlines from an array of InlineableModuleIDs
   */
  def apply(inlines: InlineableModuleID*) = new Inlines(inlines:_*)
  /**
   * implicit from ModuleID to InlineableModuleID
   */
  implicit def moduleIdToInline(moduleId: ModuleID) = InlineableModuleID(moduleId, null)
}

/**
 * Project utility for InlinedProject construction
 */
object InlinedProject {

  /**
   * check to see if a project is cloned (if not, clone it)
   * create a softlink to current directory, reload state
   */
  def inline = Command.single("inline") { (state: State, v: String) => {
    val f = file(v)
    if (!f.exists) {
      gitClone(state, v)
    }
    "ln -s %s ./%s".format(f.getCanonicalPath, f.getName) !

    State.stateOps(state).reload
  }}

  /**
   * clone a project by name.
   */
  def gitClone(state: State, v: String) {
    val extracted = Project.extract(state)
    val inlineables = extracted.get(Inlines.inlines)
    inlineables.foreach(i => println("%s: %s".format(i, i.dir)))
    val inlineable = inlineables.find(_.softLink == v).get
     "git clone %s %s".format(inlineable.scm, file("../%s".format(inlineable.dir))) !
  }

  /**
   * remove softlink, reload project state
   */
  def outline = Command.single("outline") { (state: State, v: String) => {
    "rm ./%s".format(v, v) !

    State.stateOps(state).reload
  }}

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
    .settings(libraryDependencies ++= inliners.libDeps,
              commands ++= Seq(inline, outline),
              Inlines.inlines := inlines)
  }
}
