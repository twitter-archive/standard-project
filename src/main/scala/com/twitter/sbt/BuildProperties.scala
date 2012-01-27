package com.twitter.sbt

import sbt._
import Keys._
import java.io.{File, FileWriter}
import java.util.Properties

/**
 * add various build-environment properties to a build.properties file in the built jar
 */
object BuildProperties extends Plugin {
  // most of this stuff is only supported by default in GitProjects
  import GitProject._
  /**
   * package in which to write our build.properties file.
   */
  val buildPropertiesPackage = SettingKey[String]("build-properties-package", "the package in which to write build properties")
  /**
   * the directory we should write to. Depends on build-properties-package
   */
  val buildPropertiesDir = SettingKey[String]("build-properties-dir", "the directory to write build properties to")
  /**
   * the actual file to write to. Depends on build-properties-dir
   */
  val buildPropertiesFile = SettingKey[String]("build-properties-path", "the path to write build properties to")
  /**
   * the task to write out the properties
   */
  val buildPropertiesWrite = TaskKey[Seq[File]]("build-properties-write", "writes various build properties to a file in resources")

  def writeBuildProperties(name: String,
                           version: String,
                           timestamp: Long,
                           currentRevision: Option[String],
                           branchName: Option[String],
                           lastFewCommits: Option[Seq[String]],
                           targetFile: File): Seq[File] = {
    val targetFileDir = targetFile.getParent
    new File(targetFileDir).mkdirs()
    val buildProperties = new Properties
    buildProperties.setProperty("name", name)
    buildProperties.setProperty("version", version)
    buildProperties.setProperty("build_name", timestamp.toString)
    currentRevision.foreach(buildProperties.setProperty("build_revision", _))
    branchName.foreach(buildProperties.setProperty("build_branch_name", _))
    lastFewCommits.foreach { commits =>
      buildProperties.setProperty("build_last_few_commits", commits.mkString("\n"))
    }
    val fileWriter = new FileWriter(targetFile)
    buildProperties.store(fileWriter, "")
    fileWriter.close()
    Seq(targetFile)
  }

  val newSettings: Seq[Setting[_]] = Seq(
    buildPropertiesPackage <<= (organization, name) { (o, n) => o + "." + n },
    buildPropertiesDir <<= (resourceManaged in Compile, buildPropertiesPackage) {(r, b) =>
      val packageSplits = b.split("\\.")
      val buildPropsPath = packageSplits.foldLeft(r) {(f, d) => new File(f, d)}
      buildPropsPath.getCanonicalPath                                                                        
    },
    buildPropertiesFile <<= (buildPropertiesDir) { b => new File(b, "build.properties").getCanonicalPath},
    buildPropertiesWrite <<= (name, version, buildPropertiesFile, gitProjectSha, gitBranchName, gitLastCommits) map {
      (n, v, b, sha, branch, commits) => {
      writeBuildProperties(n, v, System.currentTimeMillis, sha, branch, commits, new File(b))
    }},
    resourceGenerators in Compile <+= buildPropertiesWrite
  )
}
