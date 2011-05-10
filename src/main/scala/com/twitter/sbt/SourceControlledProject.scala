package com.twitter.sbt

import _root_.sbt._
import Process._


trait ScmAdapter {
  def isARepository: Boolean
  def currentRevision: String

  /**
   * A string containing the last few commits to the repository
   */
  def lastFewCommits: String

  /**
   * The name of the branch that the build came from
   */
  def branchName: String
}

object ScmAdapters {
  val Git = new ScmAdapter {
    override def isARepository = ("git status" ! NullLogger) == 0
    override def currentRevision = ("git rev-parse HEAD" !! NullLogger).trim
    override def lastFewCommits = ("git log --oneline --decorate --max-count=10" !! NullLogger).trim
    override def branchName = ("git symbolic-ref HEAD" !! NullLogger).trim
  }
}

trait SourceControlledProject extends Project {
  val defaultAdapterOrdering = Seq(ScmAdapters.Git)
  def adapters = adapterOrdering
  def adapterOrdering = defaultAdapterOrdering
  lazy val foundAdapters = adapters.filter(_.isARepository)

  def currentRevision: Option[String] = foundAdapters.firstOption.map { adapter =>
    adapter.currentRevision
  }

  /**
   * A string containing the last few commits to the repository
   */
  def lastFewCommits: Option[String] = foundAdapters.firstOption.map { adapter =>
    adapter.lastFewCommits
  }

  /**
   * The name of the branch that the build came from
   */
  def branchName: Option[String] = foundAdapters.firstOption.map { adapter =>
    adapter.branchName
  }

}
