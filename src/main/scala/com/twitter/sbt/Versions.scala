package com.twitter.sbt

import _root_.sbt._
import java.io.File
import Process._

trait Versions extends BasicManagedProject { self: DefaultProject =>
  class PimpedVersion(wrapped: BasicVersion) {
    private def increment(i: Option[Int]) = Some(i.getOrElse(0) + 1)

    // these methods do the wrong thing in BasicVersion. :(
    def incMicro() = BasicVersion(wrapped.major, wrapped.minor.orElse(Some(0)), increment(wrapped.micro), wrapped.extra)
    def incMinor() = BasicVersion(wrapped.major, increment(wrapped.minor), wrapped.micro.map { _ => 0 }, wrapped.extra)
    def incMajor() = BasicVersion(wrapped.major + 1, wrapped.minor.map { _ => 0 }, wrapped.micro.map { _ => 0 }, wrapped.extra)
  }
  implicit def pimpVersion(wrapped: Version) = new PimpedVersion(wrapped.asInstanceOf[BasicVersion])

  def versionBumpTask(newVersion: => Version): Task = task {
    log.info("Current version: " + projectVersion.value)
    projectVersion.update(newVersion)
    log.info("New version:     " + projectVersion.value)
    saveEnvironment()

    "git add project/build.properties" !! NullLogger
    "git commit -m " + projectVersion.value.toString !! NullLogger
    "git tag -m version-" + projectVersion.value.toString + " version-" + projectVersion.value.toString !! NullLogger

/*    println("ver = " + projectVersion.value)
    projectVersion.update(projectVersion.value.incMinor())
    println("ver = " + projectVersion.value)
    projectVersion.update(projectVersion.value.incMajor())
    println("ver = " + projectVersion.value)
//    projectVersion.update(Version.fromString("1.1.8").right.get)
"git diff" !; */
    None
  }

  lazy val versionBump = versionBumpTask(projectVersion.value.incMicro()) named("version-bump") describedAs("bump patch version")


}
