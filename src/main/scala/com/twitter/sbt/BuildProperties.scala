package com.twitter.sbt

import _root_.sbt._
import java.io.FileWriter
import java.util.{Date, Properties}
import java.text.SimpleDateFormat

trait BuildProperties extends DefaultProject with SourceControlledProject {
  def timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date)

  // make a build.properties file and sneak it into the packaged jar.
  def buildPackage = organization + "." + name
  def packageResourcesPath = buildPackage.split("\\.").foldLeft(mainResourcesOutputPath ##) { _ / _ }
  def buildPropertiesPath = packageResourcesPath / "build.properties"
  override def packagePaths = super.packagePaths +++ buildPropertiesPath

  def writeBuildPropertiesTask = task {
    packageResourcesPath.asFile.mkdirs()
    val buildProperties = new Properties
    buildProperties.setProperty("name", name)
    buildProperties.setProperty("version", version.toString)
    buildProperties.setProperty("build_name", timestamp)
    currentRevision.foreach(buildProperties.setProperty("build_revision", _))
    branchName.foreach(buildProperties.setProperty("build_branch_name", _))
    lastFewCommits.foreach(buildProperties.setProperty("build_last_few_commits", _))

    val fileWriter = new FileWriter(buildPropertiesPath.asFile)
    buildProperties.store(fileWriter, "")
    fileWriter.close()
    None
  }.dependsOn(copyResources)

  val WriteBuildPropertiesDescription = "Writes a build.properties file into the target folder."
  lazy val writeBuildProperties = writeBuildPropertiesTask dependsOn(copyResources) describedAs WriteBuildPropertiesDescription

  override def packageAction = super.packageAction dependsOn(writeBuildProperties)
}
