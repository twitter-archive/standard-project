package com.twitter.sbt

import _root_.sbt._
import Process._


trait ScmAdapter {
  def isARepository: Boolean
  def currentRevision: String
}

object ScmAdapters {
  val Git = new ScmAdapter {
    def isARepository = ("git status" ! NullLogger) == 0
    def currentRevision = ("git rev-parse HEAD" !! NullLogger).trim
  }
}

trait SourceControlledProject extends Project {
  val defaultAdapterOrdering = Seq(ScmAdapters.Git)
  def adapters = adapterOrdering
  def adapterOrdering = defaultAdapterOrdering
  lazy val foundAdapters = adapters.filter(_.isARepository)

  def currentRevision = foundAdapters.firstOption.map { adapter =>
    adapter.currentRevision
  }
}
