package com.twitter.sbt

import sbt._
import Keys._

/**
 * uses environment variables to pick the right proxy resolver to look at
 */
object DefaultRepos extends Plugin with Environmentalist {
  /**
   * the root URI of your proxy server. Used to build up various resolvers
   */
  val defaultReposProxyRootURI = SettingKey[Option[String]]("dr-proxy-root-uri", "root URI for the proxy")
  /**
   * whether or not the proxy is available
   */
  val defaultReposIsProxyAvailable = SettingKey[Boolean]("dr-is-proxy-available", "is the proxy available")
  /**
   * A proxyResolver to use instead of default repos
   */
  val defaultReposProxyResolver = SettingKey[Option[Resolver]]("dr-proxy-resolver", "proxy resolver for artifacts")
  /**
   * repos to use if we don't use a proxy
   */
  val defaultReposDefaultResolvers = SettingKey[Seq[Resolver]]("dr-default-resolvers", "a Seq of default resolvers to use if proxyResolver is undefined")
  /**
   * where to publish local artifacts
   */
  val defaultReposLocalRepoPath = SettingKey[File]("dr-local-repo-path", "the directory to use as a local repo")
  /**
   * resolver used to resolve locally published artifacts from local-repo-path
   */
  val defaultReposLocalResolver = SettingKey[Option[Resolver]]("dr-local-resolver", "local resolver for artifacts")
  /**
   * resolver used to publish artifacts to local-repo-path
   */
  val defaultReposLocalPublishResolver = SettingKey[Option[Resolver]]("dr-local-publish-resolver", "local resolver for artifacts")
  /**
   * resolver used to publish artifacts to a remote repo
   */
  val defaultReposRemotePublishResolver = SettingKey[Option[Resolver]]("dr-remote-publish-resolver", "remote resolver for artifacts")
  /**
   * where is your credentials file
   */
  val defaultReposCredentialsFile = SettingKey[Option[File]]("dr-credentials", "publish credentials")

  val newSettings = Seq(
    defaultReposProxyRootURI := Some("http://artifactory.local.twitter.com"),
    defaultReposIsProxyAvailable := environment.contains("SBT_TWITTER"),
    defaultReposProxyResolver <<= (defaultReposProxyRootURI) { root =>
      Some("artifactory-all" at "%s/repo".format(root))
    },
    // default resolvers to use if no proxy is picked
    defaultReposDefaultResolvers := Seq(
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
    defaultReposLocalRepoPath := {
      (file(environment("HOME")) / ".m2") / "repo"
    },
    // set the local resolver to use maven style patterns
    defaultReposLocalResolver <<= (defaultReposLocalRepoPath) {l =>
      Some("local-m2" at "file://%s".format(l))
    },
    defaultReposLocalPublishResolver <<= (defaultReposLocalRepoPath) {l =>
      Some(Resolver.file("local", l)(Resolver.mavenStylePatterns))
    },
    defaultReposRemotePublishResolver <<= (defaultReposProxyRootURI, version) { (proxyRoot, ver) =>
      proxyRoot map { root =>
        if (ver.endsWith("SNAPSHOT")) {
          "artifactory-snapshots-local" at "%s/libs-snapshots-local".format(root)
        } else {
          "artifactory-releases-local" at "%s/libs-releases-local".format(root)
        }
      }
    },
    defaultReposCredentialsFile := {
      Some(file(environment("HOME")) / ".artifactory-credentials")
    },
    credentials <<= (credentials, defaultReposCredentialsFile) map { (creds, publishCreds) =>
      publishCreds match {
        case Some(c) => Credentials(c) +: creds
        case _ => creds
      }
    },
    // configure resolvers for the build
    resolvers in ThisBuild <<= (resolvers,
                                defaultReposIsProxyAvailable,
                                defaultReposDefaultResolvers,
                                defaultReposProxyResolver,
                                defaultReposLocalResolver) { (r, isAvail, d, p, l) =>
      val remotes = if (p.isDefined && isAvail) {
        Seq(p.get)
      } else {
        (r ++ d)
      }
      l match {
        case Some(resolver) => resolver +: remotes
        case _ => remotes
      }
    },
    externalResolvers <<= (externalResolvers,
                                        defaultReposIsProxyAvailable,
                                        defaultReposProxyResolver) map { (r, isAvail, proxy) =>
      if (isAvail && proxy.isDefined) {
        Seq(proxy.get)
      } else {
        r
      }
    },
    publishTo <<= (publishTo, defaultReposRemotePublishResolver) { (oldPublish, defaultPublish) =>
      defaultPublish orElse oldPublish
    }
  )
}
