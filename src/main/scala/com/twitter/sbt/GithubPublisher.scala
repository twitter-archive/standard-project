package com.twitter.sbt

import _root_.sbt._
import java.io.File

// use github for publishing (not finished yet)
trait GithubPublisher extends BasicManagedProject { self: DefaultProject =>
  val githubLocalRepo = Resolver.file("github-local", new File(Resolver.userIvyRoot + "/github"))(Resolver.defaultIvyPatterns) mavenStyle()

  def publishGithubConfiguration = new DefaultPublishConfiguration("github-local", "release", false)
  def deliverGithubAction = deliverTask(deliverIvyModule, publishGithubConfiguration, UpdateLogging.DownloadOnly)
  def publishGithubAction = publishTask(deliverIvyModule, publishGithubConfiguration)

  lazy val deliverGithub: Task = deliverGithubAction dependsOn(packageToPublishActions: _*)
  lazy val publishGithub: Task = publishGithubAction dependsOn(deliverGithub, makePom)
}
