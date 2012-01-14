package com.twitter.sbt

import sbt._
import java.io.File

/**
 * provide SBT process-based utilities for dealing with git
 */
trait GitHelpers {
  def gitIsCleanWorkingTree: Boolean = {
    ("git status" !!).contains("nothing to commit (working directory clean)")
  }
}
