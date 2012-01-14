package com.twitter.sbt

import sbt._
import Keys._

/**
 * uses environment variables to pick the right proxy resolver to look at
 */
object DefaultRepos extends Plugin with Environmentalist {
  /**
   * a proxy resolver to use. Depends on SBT_PROXY_REPO, SBT_OPEN_TWITTER, SBT_TWITTER env variables.
   * If SBT_PROXY_REPO is set, it is used.
   * If not, and SBT_TWITTER is set, internal-private-proxy-resolver is used
   * If not, and SBT_OPEN_TWITTER is set, internal-public-proxy-resolver is used
   * If not, proxyResolver defaults no None
   */
  val proxyResolver = SettingKey[Option[String]]("proxy-resolver", "proxy resolver for artifacts")
  /**
   * repos to use if we don't use a proxy
   */
  val defaultResolvers = SettingKey[Seq[Resolver]]("default-resolvers", "a Seq of default resolvers to use if proxyResolver is undefined")
  /**
   * where to publish local artifacts
   */
  val localRepoPath = SettingKey[String]("local-repo-path", "the directory to use as a local repo")
  /**
   * resolver used to publish to local-repo-path
   */
  val localResolver = SettingKey[Option[Resolver]]("local-resolver", "local resolver for artifacts")
  /**
   * private proxy to use (if SBT_TWITTER is set)
   */
  val internalPrivateProxy = SettingKey[String]("internal-private-proxy-resolver", "internal proxy to use if SBT_TWITTER is set")
  /**
   * public proxy to use (if SBT_OPEN_TWITTER is set)
   */
  val internalPublicProxy = SettingKey[String]("internal-public-proxy-resolver", "internal proxy to use if SBT_OPEN_TWITTER is set")
  val newSettings = Seq(
    localRepoPath := {
      environment("HOME") + "/.m2/local-repo"
    },
    internalPrivateProxy := "http://artifactory.local.twitter.com/repo/",
    internalPublicProxy := "http://artifactory.local.twitter.com/open-source/",
    // set the local resolver to use maven style patterns
    localResolver <<= (localRepoPath) {l =>
      Some(Resolver.file("local", new java.io.File(l))(Resolver.mavenStylePatterns))
    },
    // pick a proxy based on env settings
    proxyResolver <<= (internalPrivateProxy, internalPublicProxy) { (priv, pub) =>
      environment.get("SBT_PROXY_REPO") match {
        case None =>
          if (environment.get("SBT_OPEN_TWITTER").isDefined) {
            // backward compatibility: twitter's internal open source proxy
            Some(pub)
          } else if (environment.get("SBT_TWITTER").isDefined) {
            // backward compatibility: twitter's internal proxy
            Some(priv)
          } else {
            None
          }
        case url =>
          url
      }
    },
    // default resolvers to use if no proxy is picked
    defaultResolvers := Seq(
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
    ),
    // configure resolvers for the build
    resolvers in ThisBuild <<= (resolvers, defaultResolvers, proxyResolver) { (r, d, p) =>
      p match {
        case Some(url) =>
          Seq("proxy-resolver" at url)
        case None =>
          r ++ d
      }
    }
  )
}
