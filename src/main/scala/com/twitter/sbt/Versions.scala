package com.twitter.sbt

import _root_.sbt.{BasicManagedProject, DefaultProject, Version}
import pimpedversion._

trait Versions extends BasicManagedProject with GitHelpers { self: DefaultProject =>

  def versionBumpTask(newVersion: => Version) = task {
    log.info("Current version: " + projectVersion.value)
    projectVersion.update(newVersion)
    log.info("New version:     " + projectVersion.value)
    saveEnvironment()

    val versionString = projectVersion.value.toString

    gitCommitSavedEnvironment(Some(versionString))
    gitTag("version-" + versionString)

    None
  }

  lazy val versionBump = versionBumpTask(projectVersion.value.incMicro()) named("version-bump") describedAs("bump patch version")
  lazy val versionBumpMinor = versionBumpTask(projectVersion.value.incMinor()) named("version-bump-minor") describedAs("bump minor version")
  lazy val versionBumpMajor = versionBumpTask(projectVersion.value.incMajor()) named("version-bump-major") describedAs("bump major version")
}
