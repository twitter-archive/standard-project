package com.twitter.sbt

import _root_.sbt._

trait LibDirClasspath extends StandardProject {
  def jarFileFilter: FileFilter = "*.jar"
  def libClasspath = descendents("lib", jarFileFilter)

  override def fullUnmanagedClasspath(config: Configuration) =
    super.fullUnmanagedClasspath(config) +++ libClasspath
}