package com.twitter.sbt

import _root_.sbt._
import java.io.File

trait PublishLocalWithMavenStyleBasePattern extends StandardManagedProject {
  val localIvyBasePattern = "[organisation]/[module]/[revision]/ivy-[revision](-[classifier]).[ext]"
  val localm2 = Resolver.file("localm2", new File(Resolver.userIvyRoot + "/local"))(
    Patterns(Seq(localIvyBasePattern), Seq(Resolver.mavenStyleBasePattern), true))

  override def localRepos = super.localRepos + localm2

  def usesMavenStyleBasePatternInPublishLocalConfiguration: Boolean = info.parent match {
    case Some(parent: PublishLocalWithMavenStyleBasePattern) =>
      parent.usesMavenStyleBasePatternInPublishLocalConfiguration
    case None =>
      true
  }

  override def publishLocalConfiguration = {
    if (usesMavenStyleBasePatternInPublishLocalConfiguration)
      new DefaultPublishConfiguration("localm2", "release", true)
    else
      super.publishLocalConfiguration
  }
}
