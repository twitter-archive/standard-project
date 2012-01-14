package com.twitter.sbt

import sbt._
import Keys._

/**
 * aggregate a bunch of stuff into a single plugin
 */
object StandardProject extends Plugin {
  val includes: Seq[Seq[Setting[_]]] = Seq(
    Defaults.defaultSettings,
    DefaultRepos.newSettings,
    GitProject.gitSettings,
    BuildProperties.newSettings,
    PublishLocalWithMavenStyleBasePattern.newSettings,
    PublishSourcesAndJavadocs.newSettings,
    PackageDist.newSettings,
    SubversionPublisher.newSettings,
    VersionManagement.newSettings,
    ReleaseManagement.newSettings
  )
  /**
   * newSettings is ALL THE SETTINGS
   */
  val newSettings = includes.foldLeft(Seq[Setting[_]]()) { (s, a)  => s ++ a} ++ Seq(
    exportJars := true
  )
}
