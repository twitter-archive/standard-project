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

    val versionString = projectVersion.value.toString
    val rv = (
      <x>git add project/build.properties</x> #&&
      <x>git commit -m {versionString}</x> #&&
      <x>git tag -m version-{versionString} version-{versionString}</x>
    ) !! NullLogger

    None
  }

  lazy val versionBump = versionBumpTask(projectVersion.value.incMicro()) named("version-bump") describedAs("bump patch version")
  lazy val versionBumpMinor = versionBumpTask(projectVersion.value.incMinor()) named("version-bump-minor") describedAs("bump minor version")
  lazy val versionBumpMajor = versionBumpTask(projectVersion.value.incMajor()) named("version-bump-major") describedAs("bump major version")
}
