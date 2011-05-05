package com.twitter.sbt

import _root_.sbt.{BasicManagedProject, DefaultProject, ProcessBuilder}
import _root_.sbt.Process._
import java.io.File

trait GitHelpers {
  private def run(command: ProcessBuilder) = command !! NullLogger

  def gitCommitFiles(message: String, paths: String*) {
    val task = paths.map { "git add %s".format(_): ProcessBuilder } reduceLeft { _ ## _ }
    run(task ## Seq("git", "commit",  "-m",  message))
  }
  
  def gitCommitSavedEnvironment(message: Option[String]) {
    run(
      "git add project/release.properties" ##
      "git add project/build.properties" #&&
      Seq("git", "commit",  "-m",  message.getOrElse("Updating release properties files"))
    )
  }

  def gitTag(tag: String) { run(<x>git tag -m {tag} {tag}</x>) }

  def gitIsCleanWorkingTree = run("git status").contains("nothing to commit (working directory clean)")
}
