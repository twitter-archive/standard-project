package com.twitter.sbt

import java.io.File
import scala.collection.jcl
import java.io.{BufferedReader, InputStreamReader}
import _root_.sbt._

trait Tartifactory {
  def artifactoryRoot = "http://artifactory.local.twitter.com"
  def proxyRepo = "repo"
  def snapshotDeployRepo = "libs-snapshots-local"
  def releaseDeployRepo = "libs-releases-local"
}

trait TartifactoryPublisher extends BasicManagedProject with Tartifactory {
  override def managedStyle = ManagedStyle.Maven

  val publishTo = if (version.toString.endsWith("SNAPSHOT")) {
    "Twitter Artifactory" at (artifactoryRoot + "/" + snapshotDeployRepo)
  } else {
    "Twitter Artifactory" at (artifactoryRoot + "/" + releaseDeployRepo)
  }

  override def publishTask(module: => IvySbt#Module, publishConfiguration: => PublishConfiguration) = task {
    val stdinReader = new BufferedReader(new InputStreamReader(System.in))
    System.out.print("enter your artifactory username: ")
    val username = stdinReader.readLine
    System.out.print("\nentire your artifactory password: ")
    val password = stdinReader.readLine
    Credentials.add("Artifactory Realm", "artifactory.local.twitter.com", username, password)
    super.publishTask(module, publishConfiguration).run
  }
}

@deprecated //("just use DefaultRepos")
trait TartifactoryRepos extends DefaultRepos
