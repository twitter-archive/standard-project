package com.twitter.sbt

import _root_.sbt._

trait PublishThrift extends PublishSourcesAndJavadocs {
  def packageThriftJar = defaultJarPath("-thrift.jar")
  lazy val thriftArtifact = Artifact(artifactID, "thrift", "jar", "thrift")
  def packageThriftAction = packageTask(mainSourcePath / "thrift" ##, packageThriftJar, Recursive) describedAs "package thrift jar"
  lazy val packageThrift = packageThriftAction

  // don't nuke the publish sources/javadocs goodness
  override lazy val publish = super.publishAction dependsOn(packageDocs, packageSrc, packageThrift)
  override lazy val publishLocal = super.publishLocalAction dependsOn(packageDocs, packageSrc, packageThrift)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc, packageThrift)
}
