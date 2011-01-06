package com.twitter.sbt

import _root_.sbt._

trait GeneratedSources extends DefaultProject {
  def generatedJavaDirectoryName = "gen-java"
  def generatedRubyDirectoryName = "gen-rb"

  def generatedJavaPath = outputPath / generatedJavaDirectoryName
  def generatedRubyPath = outputPath / generatedRubyDirectoryName

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedJavaDirectoryName ##)

  lazy val cleanGenerated = (cleanTask(generatedJavaPath) && cleanTask(generatedRubyPath)) describedAs
    "Clean generated source folders"

  override def cleanAction = super.cleanAction dependsOn(cleanGenerated)
}
