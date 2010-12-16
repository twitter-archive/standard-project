package com.twitter.sbt

import collection.mutable.{HashSet, HashMap, ListBuffer}
import scala.collection.jcl
import _root_.sbt._

trait InlineDependencies extends BasicManagedProject {
  val inlineEnvironment = jcl.Map(System.getenv())
  val inlinedLibraryDependencies = new HashSet[ModuleID]()
  val inlinedSubprojects = new ListBuffer[(String, _root_.sbt.Project)]()
  val inlinedModules = new HashMap[String, ModuleID]()

  override def libraryDependencies = {
    super.libraryDependencies ++ inlinedLibraryDependencies
  }

  override def subProjects = {
    Map() ++ super.subProjects ++ inlinedSubprojects
  }

  override def shouldCheckOutputDirectories = false

  def inline(m: ModuleID): Unit = inline(m, m.name)
  def inline(m: ModuleID, relativePath: String) {
    val path = Path.fromFile("../") / relativePath
    inlinedModules += (m.name -> m)
    if (inlineEnvironment.get("SBT_INLINE").isDefined && path.isDirectory)
      inlinedSubprojects += (m.name -> project(path))
    else
      inlinedLibraryDependencies += m
  }

  // TODO: Can we do this transitively?
  lazy val checkInlineVersions = task {
    val namedSubprojects = Map() ++ inlinedSubprojects
    inlinedModules foreach { case (name, module) =>
      namedSubprojects.get(name) foreach { subProject =>
        if (module.revision != subProject.version.toString) {
          println("\"%s\" version mismatch: %s is specified but %s is inlined".format(
            name, module.revision, subProject.version))
        }
      }
    }

    None
  }
}
