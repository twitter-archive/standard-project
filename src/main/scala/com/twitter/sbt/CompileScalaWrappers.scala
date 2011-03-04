package com.twitter.sbt

import _root_.sbt._
import java.io._
import org.jruby.embed._

/**
 * This code compiles scala wrappers for thrift.  I can't figure out how to test inside this package (recursive self sbt plugin tests?!),
 * so I created a sample twitter-local project called "quack", that has an extremely heinous thrift IDL, which exercises every
 * thrift type (with nesting and structs).  Grab it and compile it, to make sure this still works :).
 */
trait CompileScalaWrappers extends DefaultProject with CompileFinagleThrift {
  def scalaThriftTargetNamespace: String
  def rubyThriftNamespace: String
  def javaThriftNamespace = scalaThriftTargetNamespace + ".thrift"

  lazy val autoCompileThriftScala = task {
    val name = "/ruby/codegen.rb"
    val stream = getClass.getResourceAsStream(name)
    val reader = new InputStreamReader(stream)
    val container = new ScriptingContainer(LocalContextScope.SINGLETON, LocalVariableBehavior.TRANSIENT)
    container.runScriptlet(reader, "__TMP__")
    val module = container.runScriptlet("Codegen")
    container.callMethod(module, "run", (outputPath / generatedRubyDirectoryName ##).toString,
      (outputPath / generatedScalaDirectoryName ##).toString, javaThriftNamespace, rubyThriftNamespace, scalaThriftTargetNamespace)

    None
  }.dependsOn(autoCompileThriftRuby)

  def generatedScalaDirectoryName = "gen-scala"
  def generatedScalaPath = outputPath / generatedScalaDirectoryName

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)
  override def compileAction = super.compileAction dependsOn(autoCompileThriftScala)
  override def cleanAction = super.cleanAction dependsOn(cleanTask(generatedScalaPath))
}
