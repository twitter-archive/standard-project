package com.twitter.sbt

import scala.collection.Set
import _root_.sbt._

/*
 * robey's notes:
 * - BasicManagedProject mixes in ReflectiveRepositories which defines "repositories" by
 *   reflectively collecting all vals that are of type Repository.
 * - so we should be able to pick up all arbitrary repos by calling super.repositories
 */
trait DefaultRepos extends BasicManagedProject with Environmentalist {
  override def repositories = {
    val extraRepos = if (environment.get("SBT_TWITTER").isDefined) {
      val localURL = new java.net.URL("http://binaries.local.twitter.com/maven/")
      val ivyXmlPatterns = List("[organization]/[module]/[revision]/ivy-[revision].xml")
      val ivyArtifactPatterns = List("[organization]/[module]/[revision]/[artifact]-[revision].[ext]")

      val binariesIvyStyleRepo = Resolver.url("twitter internal old ivy-style paths",
                                              localURL)(Patterns(ivyXmlPatterns, ivyArtifactPatterns, false))
      List(
        "twitter-private-m2" at "http://binaries.local.twitter.com/maven/", binariesIvyStyleRepo
      )
    } else {
      Nil
    }

    val repos = List(
      "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/",
      "twitter.com" at "http://maven.twttr.com/",
      "powermock-api" at "http://powermock.googlecode.com/svn/repo/",
      "scala-tools.org" at "http://scala-tools.org/repo-releases/",
      "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/testing/",
      "oauth.net" at "http://oauth.googlecode.com/svn/code/maven",
      "download.java.net" at "http://download.java.net/maven/2/",
      "atlassian" at "https://m2proxy.atlassian.com/repository/public/",

      // for netty:
      "jboss" at "http://repository.jboss.org/nexus/content/groups/public/"
    ) ++ extraRepos

    Set(repos: _*)
  }
}
