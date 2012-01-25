package com.twitter.sbt

import sbt._
import Keys._

/**
 * a plugin that supports publishing a stable version of a project to source control and remote repos
 */
object ReleaseManagement extends Plugin with GitHelpers {
  import VersionManagement._

  /**
   * is the current repo ready for a release
   */ 
  val releaseReady = TaskKey[Boolean]("release-ready", "checks to see if current source tree and project can be published")

  /**
   * an ordered set of tasks/commands that need to be run for the publish
   */
  val releasePublishTasks = SettingKey[Seq[String]]("release-publish-tasks", "a list of tasks to execute (in order) for publishing a release")

  /**
   * checks release-ready, and if the answer is yes, runs release-publish-tasks
   */
  def releasePublish = Command.command("release-publish", Help.empty) { (state: State) =>
    val extracted = Project.extract(state)
    import extracted._
    Project.runTask(releaseReady, state) match {
      // returned if releaseReady doesn't exist in the current state
      case None => {
        println("no release-ready task defined")
        state.fail
      }
      // returned if releaseReady failed
      case Some((s, Inc(i))) => {
        Incomplete.show(i.tpe)
        state.fail
      }
      case Some((s, Value(false))) => {
        println("Stopping release")
        state.fail
      }
      case Some((s, Value(true))) => {
        // we're ok for release, so return a new state with the publish tasks appended
        val pubTasks = extracted.get(releasePublishTasks)
        s.copy(remainingCommands = pubTasks ++ s.remainingCommands)
      }
    }
  }

  val newSettings = Seq(
    releasePublishTasks := Seq("release-ready", "version-to-stable", "publish-local", "publish", "git-commit", "git-tag", "version-bump-patch", "version-to-snapshot", "git-commit"),
    releaseReady <<= (version, libraryDependencies) map { (v, deps) =>
      val tags = ("git tag -l grep + %s".format(v) !!).trim
      // we don't release dirty trees
      if (!gitIsCleanWorkingTree) {
        println("Working directory is not clean")
        false
      } else if (deps.exists(_.revision.contains("SNAPSHOT"))) {
        // we don't release with snapshot dependencies
        println("Build has snapshotted depndencies")
        false
      } else if (tags.contains(v) && !tags.contains("SNAPSHOT")) {
        // we don't double-release
        println("Cannot tag release version %s. Tag already exists".format(v))
        false
      } else {
        println("Current project is ok for release")
        true
      }
    }
  )

  /**
   * make release-publish available to projects
   */
  override lazy val settings = Seq(commands += releasePublish)

}
