package com.twitter.sbt

import _root_.sbt.{BasicManagedProject, BasicDependencyProject, Version}
import pimpedversion._

trait Versions extends BasicManagedProject with GitHelpers {
  def versionBumpTask(newVersion: => Version) = task {
    info.parent match {
      case Some(_: Versions) =>
        // this is a sub-project, don't change version here, let the parent do it
        None

      case _ =>
        log.info("Current version: " + projectVersion.value)
        projectVersion.update(newVersion)
        log.info("New version:     " + projectVersion.value)
        saveEnvironment()

        gitCommitSavedEnvironment(Some(projectVersion.value.toString))

        None
    }
  }

  lazy val versionBump = versionBumpTask(projectVersion.value.incMicro())
    .describedAs("bump patch version")

  lazy val versionBumpMinor = versionBumpTask(projectVersion.value.incMinor())
    .describedAs("bump minor version")

  lazy val versionBumpMajor = versionBumpTask(projectVersion.value.incMajor())
    .describedAs("bump major version")
}
