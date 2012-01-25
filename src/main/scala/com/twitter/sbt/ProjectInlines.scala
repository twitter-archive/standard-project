package com.twitter.sbt

import sbt._
import Keys._

// from coderspiel
class Local (locals: (String, String, ModuleID)*) {
  def addDeps (p: Project): Project = (locals collect {
    case (id, subp, dep) if (file(id).exists) => symproj(file(id), subp)
  }).foldLeft(p) { _ dependsOn _ }
  def libDeps: Seq[ModuleID] = locals collect {
    case (id, subp, dep) if (!file(id).exists) => dep
  }
  private def symproj (dir: File, subproj: String = null) =
    if (subproj == null) RootProject(dir) else ProjectRef(dir, subproj)
}

object Local {
  def apply(locals: (String, String, ModuleID)*) = new Local(locals:_*)
}

object InlinedProject {
  def inline = Command.single("inline") { (state: State, v: String) => {
    "ln -s ../%s ./%s".format(v, v) !

    State.stateOps(state).reload
  }}
  def outline = Command.single("outline") { (state: State, v: String) => {
    "rm ./%s".format(v, v) !

    State.stateOps(state).reload
  }}

  def apply(id: String, base: File,
            aggregate: => Seq[ProjectReference] = Nil,
            dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
            delegates: => Seq[ProjectReference] = Nil,
	    settings: => Seq[Setting[_]] = Defaults.defaultSettings,
            configurations: Seq[Configuration] = Configurations.default,
            locals: Local): Project = {
    locals.addDeps(Project(id, base, aggregate, dependencies, delegates, settings, configurations))
    .settings(libraryDependencies ++= locals.libDeps,
              commands ++= Seq(inline, outline))
  }
}
