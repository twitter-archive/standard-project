package com.twitter.sbt

import _root_.sbt._

trait PublishSourcesAndJavadocs extends DefaultProject {
  // need to ask scaladoc not to try to understand any generated code (for example, thrift stuff):
  def docSources = sources((mainJavaSourcePath##) +++ (mainScalaSourcePath##))
  override def docAction = scaladocTask(mainLabel, docSources, mainDocPath, docClasspath, documentOptions).dependsOn(compile)

  override def packageDocsJar = defaultJarPath("-javadoc.jar")
  override def packageSrcJar= defaultJarPath("-sources.jar")
  lazy val sourceArtifact = Artifact.sources(name)
  lazy val docsArtifact = Artifact.javadoc(name)
  override lazy val publish = super.publishAction dependsOn(packageDocs, packageSrc)
  override lazy val publishLocal = super.publishLocalAction dependsOn(packageDocs, packageSrc)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
}
