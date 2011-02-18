package com.twitter.sbt

import _root_.sbt._
import java.io._

/**
 * This code is relatively young.  It's usable for some purposes now, but you might need an adventurous spirit to get involved at this stage.
 */
trait CompileScalaWrappers extends DefaultProject with CompileFinagleThrift {
  def scalaThriftTargetNamespace: String
  def scalaThriftNamespace = scalaThriftTargetNamespace + ".thrift"
  
  lazy val autoCompileScalaThrift = task {
    println("If this errors out, you might need to `gem install thrift_scala_wrappers`")
    import Process._
    (execTask { "thrift_scala_wrappers %s %s %s %s".format( (outputPath / generatedRubyDirectoryName ##).toString, (outputPath / generatedScalaDirectoryName ##).toString, scalaThriftNamespace, scalaThriftTargetNamespace) }).run
    
    None
  }
  
  def generatedScalaDirectoryName = "gen-scala"
  def generatedScalaPath = outputPath / generatedScalaDirectoryName

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)
  override def compileAction = super.compileAction dependsOn(autoCompileScalaThrift)
  override def cleanAction = super.cleanAction dependsOn(cleanTask(generatedScalaPath))
}