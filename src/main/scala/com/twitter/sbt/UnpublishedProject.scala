package com.twitter.sbt

import _root_.sbt._

trait UnpublishedProject extends BasicManagedProject {
  override def publishTask(module: => IvySbt#Module, publishConfiguration: => PublishConfiguration) = task {
    log.info(name + ": skipping publish")
    None
  }

  override def deliverTask(module: => IvySbt#Module, deliverConfiguration: => PublishConfiguration, logging: => UpdateLogging.Value) = task {
    log.info(name + ": skipping deliver")
    None
  }
}
