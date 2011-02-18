package com.twitter.sbt

import _root_.sbt._
import java.io._
import org.jruby.embed._

/**
 * This code is relatively young.  It's usable for some purposes now, but you might need an adventurous spirit to get involved at this stage.
 */
trait CompileScalaWrappers extends DefaultProject with CompileFinagleThrift {
  def scalaThriftTargetNamespace: String
  def scalaThriftNamespace = scalaThriftTargetNamespace + ".thrift"
  
  lazy val autoCompileScalaThrift = task {    
    val name = "/ruby/codegen.rb"
    val stream = getClass.getResourceAsStream(name)
    val reader = new InputStreamReader(stream)
    val container = new ScriptingContainer()
    container.setClassLoader("".getClass.getClassLoader)
    container.runScriptlet(reader, name)
    val module = container.runScriptlet("Codegen")
    container.callMethod(module, "run", (outputPath / generatedRubyDirectoryName ##).toString, (outputPath / generatedScalaDirectoryName ##).toString, scalaThriftNamespace, scalaThriftTargetNamespace)  
    
    None
  }
  
  def generatedScalaDirectoryName = "gen-scala"
  def generatedScalaPath = outputPath / generatedScalaDirectoryName

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)
  override def compileAction = super.compileAction dependsOn(autoCompileScalaThrift)
  override def cleanAction = super.cleanAction dependsOn(cleanTask(generatedScalaPath))
}