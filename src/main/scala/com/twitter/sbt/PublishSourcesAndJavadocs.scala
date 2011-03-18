package com.twitter.sbt

import _root_.sbt._

trait PublishSourcesAndJavadocs extends DefaultProject {
  override def packageDocsJar = defaultJarPath("-javadoc.jar")
  override def packageSrcJar= defaultJarPath("-sources.jar")
  val sourceArtifact = Artifact.sources(artifactID)
  val docsArtifact = Artifact.javadoc(artifactID)
  override lazy val publish = super.publishAction dependsOn(packageDocs, packageSrc)
  override lazy val publishLocal = super.publishLocalAction dependsOn(packageDocs, packageSrc)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
}
