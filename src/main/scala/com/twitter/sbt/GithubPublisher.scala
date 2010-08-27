package com.twitter.sbt

import _root_.sbt._
import java.io.File
import Process._

// use github for publishing (not finished yet)
trait GithubPublisher extends BasicManagedProject { self: DefaultProject =>
  val githubFolder = new File(Resolver.userIvyRoot, "github")
  val githubLocalRepo = Resolver.file("github-local", githubFolder)(Resolver.mavenStylePatterns) mavenStyle()
  val git = "git --git-dir=" + githubFolder + "/.git --work-tree=" + githubFolder

  val prepGithubTask = task {
    if (!new File(githubFolder, ".git").isDirectory) {
      Some("No github folder found! Create it first: git clone git@github.com:twitter/repo.git ~/.ivy2/github")
    } else {
      val rv = ((
        <x>{git} fetch</x> #&&
        (<x>{git} branch gh-pages origin/gh-pages</x> #|| "echo Ignore that. We are fine.") #&&
        <x>{git} checkout gh-pages</x> #&&
        <x>{git} merge origin/gh-pages</x>
      )!)
      if (rv == 0) None else Some("ERROR: " + rv)
    }
  } named("prep-github")
  def prepGithubAction = prepGithubTask

  val commitGithubTask = task {
    val rv = ((
      <x>{git} add .</x> #&&
      <x>{git} commit -m sbt-commit</x> #&&
      <x>{git} push origin gh-pages</x>
    )!)
    if (rv == 0) None else Some("ERROR: " + rv)
  } named("commit-github")
  def commitGithubAction = commitGithubTask

  def publishGithubConfiguration = new DefaultPublishConfiguration("github-local", "release", false)
  def deliverGithubFolderAction = deliverTask(deliverIvyModule, publishGithubConfiguration, UpdateLogging.DownloadOnly) dependsOn(makePom) named("deliver-github")
  def publishGithubFolderAction = publishTask(deliverIvyModule, publishGithubConfiguration) dependsOn(deliverGithubFolderAction)

  def publishGithubAction = prepGithubAction && publishGithubFolderAction && commitGithubAction
  lazy val publishGithub: Task = publishGithubAction

  lazy val deliverGithub: Task = deliverGithubFolderAction dependsOn(packageToPublishActions: _*)
}
