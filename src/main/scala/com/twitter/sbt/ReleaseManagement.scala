package com.twitter.sbt

import _root_.sbt.{BasicManagedProject, DefaultProject}
import _root_.sbt.Process._
import pimpedversion._

trait ReleaseManagement extends BasicManagedProject with GitHelpers { self: DefaultProject =>
  def prepareForReleaseTask = task {
    val version = projectVersion.value.toString
    val tags = ("git tag -l | grep " + version) !!

    if (!gitIsCleanWorkingTree) {
      Some("Cannot publish release. Working directory is not clean.")
    } else if (libraryDependencies.exists(_.revision.contains("SNAPSHOT"))) {
      Some("Cannot publish a release with snapshotted dependencies.")
    } else if (tags.contains(version) && !tags.contains("SNAPSHOT")) {
      Some("Cannot publish release version '" + version + "'. Tag for that release already exists.")
    } else {
      stripSnapshotExtraTask.run
    }
  }

  lazy val prepareForRelease = prepareForReleaseTask

  private def stripSnapshotExtraTask = task {
    projectVersion.update(projectVersion.value.stripSnapshot())
    saveEnvironment()

    None
  }

  def finalizeReleaseTask = task {
    val version = projectVersion.value
    val newVersion = projectVersion.value.incMicro().addSnapshot()

    // commit and tag the release
    gitCommitSavedEnvironment(Some(version.toString))
    gitTag("version-" + version.toString)

    // reset version to the new working version
    projectVersion.update(newVersion)
    saveEnvironment()
    gitCommitSavedEnvironment(Some(newVersion.toString))

    None
  }

  lazy val finalizeRelease = finalizeReleaseTask

  def publishReleaseTask = task {
    val exitCode = ("sbt +publish" !)
    if (exitCode == 0) None else Some("sbt +publish exit code " + exitCode)
  }

  val PublishReleaseDescription = "Publish a release to maven. commits and tags version in git."
  lazy val publishRelease =
    task { log.info("Publishing new release: " + projectVersion.value.stripSnapshot()); None } &&
    prepareForReleaseTask &&
    publishReleaseTask &&
    finalizeReleaseTask describedAs
    PublishReleaseDescription
}
