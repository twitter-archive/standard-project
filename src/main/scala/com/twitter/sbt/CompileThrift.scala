package com.twitter.sbt

import _root_.sbt._

trait CompileThrift extends DefaultProject
  with GeneratedSources
  with Environmentalist
{
  // thrift generation.
  def compileThriftAction(lang: String) = task {
    import Process._
    outputPath.asFile.mkdirs()
    val thriftBin = environment.get("THRIFT_BIN").getOrElse("thrift")
    val tasks = thriftSources.getPaths.map { path =>
      execTask { "%s --gen %s -o %s %s".format(thriftBin,lang, outputPath.absolutePath, path) }
    }
    if (tasks.isEmpty) None else tasks.reduceLeft { _ && _ }.run
  }

  def thriftSources = (mainSourcePath / "thrift" ##) ** "*.thrift"

  lazy val compileThriftJava = compileThriftAction("java") describedAs("Compile thrift into java")
  lazy val compileThriftRuby = compileThriftAction("rb") describedAs("Compile thrift into ruby")

  /** override to disable auto-compiling of thrift */
  def autoCompileThriftEnabled = true

  lazy val autoCompileThriftJava = task {
    if (autoCompileThriftEnabled) compileThriftJava.run 
    else {
      log.info(name+": not auto-compiling thrift-java; you may need to run compile-thift-java manually")
      None
    }
  }

  lazy val autoCompileThriftRuby = task {
    if (autoCompileThriftEnabled) compileThriftRuby.run 
    else {
      log.info(name+": not auto-compiling thrift-ruby; you may need to run compile-thift-ruby manually")
      None
    }
  }

  override def compileAction = super.compileAction dependsOn(autoCompileThriftJava, autoCompileThriftRuby)
}
