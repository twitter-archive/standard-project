package com.twitter.sbt

import scala.collection.Set
import _root_.sbt._

/*
 * BasicManagedProject mixes in ReflectiveRepositories which defines "repositories" by
 * reflectively collecting all vals that are of type Repository.
 */
trait DefaultRepos extends StandardManagedProject with Environmentalist {
  val proxyRepo = environment.get("SBT_PROXY_REPO") match {
    case None =>
      if (environment.get("SBT_OPEN_TWITTER").isDefined) {
        // backward compatibility: twitter's internal open source proxy
        Some("http://artifactory.local.twitter.com/open-source/")
      } else if (environment.get("SBT_TWITTER").isDefined) {
        // backward compatibility: twitter's internal proxy
        Some("http://artifactory.local.twitter.com/repo/")
      } else {
        None
      }
    case url =>
      url
  }

  override def repositories = {
    val defaultRepos = List(
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
    )

    proxyRepo match {
      case Some(url) =>
        localRepos + ("proxy-repo" at url)
      case None =>
        super.repositories ++ Set(defaultRepos: _*)
    }
  }

  override def ivyRepositories = {
    proxyRepo match {
      case Some(url) =>
        Seq(Resolver.defaultLocal(None)) ++ repositories.toList
      case None =>
        super.ivyRepositories
    }
  }
}
