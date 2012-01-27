package com.twitter.sbt
import _root_.sbt._

trait ArtifactoryPublisher extends SubversionPublisher with Environmentalist {
  def svnConfiguration: DefaultPublishConfiguration = {
    subversionResolver match {
      case None =>
        super.publishConfiguration
      case Some(resolver) =>
        new DefaultPublishConfiguration(resolver.getName(), "release", true)
    }
  }

  val proxyPublishRepo = environment.get("SBT_PROXY_PUBLISH_REPO") match {
    case None =>
      Some("http://artifactory.local.twitter.com/")
    case url =>
      url
  }

  /**
   * is this a public or local release?
   */
  def proxyQualifier = "local"

  def proxySnapshotOrRelease = {
    if (version.toString.endsWith("SNAPSHOT")) {
      "snapshot"
    } else {
      "release"
    }
  }

  def proxyRepoPublishTarget = {
    // extra s is not a typo here. artifactory pluralizes
    "libs-%ss-%s".format(proxySnapshotOrRelease, proxyQualifier)
  }

  def proxyPublish = environment.get("SBT_CI").isDefined

  override def repositories = {
    if (proxyPublish) {
      proxyPublishRepo match {
        case Some(repo) => {
          Credentials(Path.userHome / ".artifactory-credentials", log)
          val publishRepo = "%s%s".format(repo, proxyRepoPublishTarget)
          log.info("running under CI, will publish to %s".format(publishRepo))
          super.repositories + ("proxy-publish" at publishRepo)
        }
        case _ => super.repositories
      }
    } else {
      super.repositories
    }
  }

  override def publishConfiguration: DefaultPublishConfiguration = {
    if (proxyPublish) {
      proxyPublishRepo match {
        case Some(repo) => {
          new DefaultPublishConfiguration("proxy-publish", proxySnapshotOrRelease, false)
        }
        case _ => svnConfiguration
      }
    } else {
      svnConfiguration
    }
  }
}

trait ArtifactoryPublicPublisher extends ArtifactoryPublisher {
  override def proxyQualifier = "public"
}
