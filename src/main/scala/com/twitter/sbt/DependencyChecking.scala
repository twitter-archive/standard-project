package com.twitter.sbt

import _root_.sbt._

trait DependencyChecking extends DefaultProject {
  lazy val checkDepsExist = task { checkDepsExistOn(this) }

  protected def checkDepsExistOn(project: BasicManagedProject): Option[String] = {
    project.info.parent match {
      case Some(parent: BasicManagedProject) => checkDepsExistOn(parent)
      case _ =>
    }

    if (!project.libraryDependencies.isEmpty && !project.managedDependencyRootPath.asFile.exists) {
      Some("You must run 'sbt update' first to download dependent jars.")
    } else if (!(organization contains ".")) {
      Some("Your organization name doesn't look like a valid package name. It needs to be something like 'com.example'.")
    } else {
      None
    }
  }

  override def compileAction = super.compileAction dependsOn(checkDepsExist)
}