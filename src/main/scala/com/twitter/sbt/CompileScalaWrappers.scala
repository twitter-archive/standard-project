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

  @Deprecated
  def rubyThriftNamespace: String = throw new RuntimeException("Please override def rubyThriftNamespace or originalThriftNamespaces (latter preferred)")
  @Deprecated
  def javaThriftNamespace = scalaThriftTargetNamespace + ".thrift"

  // Preferred, because it handles compiling multiple namespaces
  def originalThriftNamespaces = Map(rubyThriftNamespace->javaThriftNamespace)

  lazy val autoCompileScalaThrift = task {
    val name = "/ruby/codegen.rb"
    val stream = getClass.getResourceAsStream(name)
    val reader = new InputStreamReader(stream)
    val container = new ScriptingContainer(LocalContextScope.SINGLETON, LocalVariableBehavior.TRANSIENT)
    container.runScriptlet(reader, "__TMP__")
    val module = container.runScriptlet("Codegen")
    for ((_rubyThriftNamespace, _javaThriftNamespace) <- originalThriftNamespaces) {
      container.callMethod(module, "run", (outputPath / generatedRubyDirectoryName ##).toString, (outputPath / generatedScalaDirectoryName ##).toString, _javaThriftNamespace, _rubyThriftNamespace, scalaThriftTargetNamespace)
    }
    None
  }.dependsOn(autoCompileThriftRuby)

  def generatedScalaDirectoryName = "gen-scala"
  def generatedScalaPath = outputPath / generatedScalaDirectoryName

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)
  override def compileAction = super.compileAction dependsOn(autoCompileScalaThrift)
  override def cleanAction = super.cleanAction dependsOn(cleanTask(generatedScalaPath))
}


// @Deprecated
// def rubyThriftNamespace: String = throw new RuntimeException("Please override def rubyThriftNamespace or originalThriftNamespaces (latter preferred)")
// @Deprecated
// def javaThriftNamespace = scalaThriftTargetNamespace + ".thrift"
// 
// // Preferred, because it handles compiling multiple namespaces
// def originalThriftNamespaces = Map(rubyThriftNamespace->javaThriftNamespace)
// 
// lazy val autoCompileThriftScala = task {
//   val name = "/ruby/codegen.rb"
//   val stream = getClass.getResourceAsStream(name)
//   val reader = new InputStreamReader(stream)
//   val container = new ScriptingContainer(LocalContextScope.SINGLETON, LocalVariableBehavior.TRANSIENT)
//   container.runScriptlet(reader, "__TMP__")
//   val module = container.runScriptlet("Codegen")
//   for ((_rubyThriftNamespace, _javaThriftNamespace) <- originalThriftNamespaces) {
//     container.callMethod(module, "run", (outputPath / generatedRubyDirectoryName ##).toString,
//       (outputPath / generatedScalaDirectoryName ##).toString, _javaThriftNamespace, _rubyThriftNamespace, scalaThriftTargetNamespace)
//   }
// 
//   None
// }.dependsOn(autoCompileThriftRuby)
