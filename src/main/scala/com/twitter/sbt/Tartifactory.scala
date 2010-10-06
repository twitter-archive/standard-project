package com.twitter.sbt

import java.io.File
import scala.collection.jcl
import _root_.sbt._

trait Tartifactory {
  def artifactoryRoot = "http://artifactory.local.twitter.com"
  def proxyRepo = "repo"
  def snapshotDeployRepo = "libs-snapshots-local"
  def releaseDeployRepo = "libs-releases-local"
}

trait TartifactoryPublisher extends BasicManagedProject with Tartifactory { self: DefaultProject =>
  override def managedStyle = ManagedStyle.Maven
  Credentials(Path.userHome / ".ivy2" / "twitter-credentials", log)
  val publishTo = if (version.toString.endsWith("SNAPSHOT")) {
    "Twitter Artifactory" at (artifactoryRoot + "/" + snapshotDeployRepo)
  } else {
    "Twitter Artifactory" at (artifactoryRoot + "/" + releaseDeployRepo)
  }
}

trait TartifactoryRepos extends BasicManagedProject with Tartifactory { self: DefaultProject =>
  private val tartEnv = jcl.Map(System.getenv())
  val internalRepos = List("artifactory.remote" at (artifactoryRoot + "/" + proxyRepo))
  val externalRepos = List(
    "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/",
    "lag.net" at "http://www.lag.net/repo/",
    "old.twitter.com" at "http://www.lag.net/nest/",
    "twitter.com" at "http://maven.twttr.com/",
    "powermock-api" at "http://powermock.googlecode.com/svn/repo/",
    "scala-tools.org" at "http://scala-tools.org/repo-releases/",
    "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/testing/",
    "reucon" at "http://maven.reucon.com/public/",
    "oauth.net" at "http://oauth.googlecode.com/svn/code/maven",
    "download.java.net" at "http://download.java.net/maven/2/",
    "atlassian" at "https://m2proxy.atlassian.com/repository/public/")

  override def repositories: Set[Resolver] = {
    val useArtifactory = tartEnv.get("ARTIFACTORY_TWITTER") match {
      case Some(v) => v != "false"
      case _ => true
    }

    val projectRepos = if (useArtifactory) {
      internalRepos
    } else {
      externalRepos
    }
    super.repositories ++ projectRepos
  }
}
