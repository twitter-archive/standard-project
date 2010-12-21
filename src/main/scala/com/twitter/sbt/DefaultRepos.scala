package com.twitter.sbt

import java.io.File
import scala.collection.jcl
import _root_.sbt._

trait DefaultRepos extends BasicManagedProject { self: DefaultProject =>

  val defEnv = jcl.Map(System.getenv())

  val ibiblioRepository  = "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/"
  val twitterRepository  = "twitter.com" at "http://maven.twttr.com/"
  val powerMock          = "powermock-api" at "http://powermock.googlecode.com/svn/repo/"
  val scalaToolsReleases = "scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val scalaToolsTesting  = "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/testing/"
  val oauthDotNet        = "oauth.net" at "http://oauth.googlecode.com/svn/code/maven"
  val javaDotNet         = "download.java.net" at "http://download.java.net/maven/2/"
  val atlassian          = "atlassian" at "https://m2proxy.atlassian.com/repository/public/"

  // for netty:
  val jboss              = "jboss" at "http://repository.jboss.org/nexus/content/groups/public/"

  val localURL = new java.net.URL("http://binaries.local.twitter.com/maven/")
  val ivyXmlPatterns = List("[organization]/[module]/[revision]/ivy-[revision].xml")
  val ivyArtifactPatterns = List("[organization]/[module]/[revision]/[artifact]-[revision].[ext]")

  // tears.  TEARS.
  val binariesIvyStyleRepo = Resolver.url("twitter internal old ivy-style paths",
                                          localURL)(Patterns(ivyXmlPatterns, ivyArtifactPatterns, false))

  val twitterPrivateRepo = if (defEnv.get("SBT_TWITTER").isDefined) {
    new MavenRepository("twitter-private-m2", "http://binaries.local.twitter.com/maven/")
  } else {
    DefaultMavenRepository
  }
}
