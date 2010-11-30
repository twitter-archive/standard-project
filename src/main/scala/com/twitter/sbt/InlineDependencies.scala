package com.twitter.sbt

import collection.mutable.{HashSet, ListBuffer}
import scala.collection.jcl
import _root_.sbt._

trait InlineDependencies extends BasicManagedProject { self: DefaultProject =>
  val inlineEnvironment = jcl.Map(System.getenv())
  val inlinedLibraryDependencies = new HashSet[ModuleID]()
  val inlinedSubprojects = new ListBuffer[(String, _root_.sbt.Project)]()

  override def libraryDependencies = {
    super.libraryDependencies ++ inlinedLibraryDependencies
  }

  override def subProjects = {
    Map() ++ super.subProjects ++ inlinedSubprojects
  }

  def inline(m: ModuleID) = {
    val path = Path.fromFile("../" + m.name)
    if (inlineEnvironment.get("SBT_INLINE").isDefined && path.isDirectory)
      inlinedSubprojects += (m.name -> project(path))
    else
      inlinedLibraryDependencies += m
  }
}