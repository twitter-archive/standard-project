package com.twitter.sbt

import _root_.sbt.{BasicManagedProject, DefaultProject}
import _root_.sbt.Process._
import pimpedversion._

trait ReleaseManagement extends BasicManagedProject with GitHelpers { self: DefaultProject =>
  private def checkCleanWorkingTreeTask = task {
    if ( !gitIsCleanWorkingTree ) error("cannot publish release. working directory is not clean")
    None
  }

  private def checkForSnapshottedDependenciesTask = task {
    val myName = self.getClass.getName
    // TODO: do this without grepping the source code... :/
    val output = "grep '-SNAPSHOT' project/build/*Project.scala" !! NullLogger

    if ( output.contains("SNAPSHOT") )
      error ("cannot publish a release with snapshotted dependencies")

    None
  }

  private def checkExistingTagTask = task {
    val version = projectVersion.value.toString
    val output = ("git tag -l | grep " + version) !! NullLogger

    if ( output.contains(version) && !output.contains("-SNAPSHOT") )
      error ("cannot publish release version '" + version + "'. tag already exists.")

    None
  }

  private def stripSnapshotExtraTask = task {
    projectVersion.update(projectVersion.value.stripSnapshot())
    saveEnvironment()

    None
  }

  private def finalizeReleaseTask = task {
    val version = projectVersion.value
    val newVersion = projectVersion.value.incMicro().addSnapshot()

    // commit and tag the release
    gitCommitSavedEnvironment(Some(version.toString))
    gitTag("version-" + version.toString)

    // reset version to the new working version
    projectVersion.update(newVersion); saveEnvironment()
    gitCommitSavedEnvironment(Some("base " + newVersion.toString))

    None
  }

  val PublishReleaseDescription = "Publish a release to maven. commits and tags version in git."
  lazy val publishRelease = task { None } dependsOn(
    task { log.info("Publishing new release: " + projectVersion.value.stripSnapshot()); None },
    checkCleanWorkingTreeTask,
    checkForSnapshottedDependenciesTask,
    checkExistingTagTask,
    stripSnapshotExtraTask,
    publish,
    finalizeReleaseTask
  ) describedAs PublishReleaseDescription
}
