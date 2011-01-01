package com.twitter.sbt

import _root_.sbt._


trait CompileThrift extends DefaultProject with Environmentalist {
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
  def thriftJavaPath = outputPath / "gen-java"
  def thriftRubyPath = outputPath / "gen-rb"

  // turn on more warnings.
  override def compileOptions = super.compileOptions ++ Seq(Unchecked)

  lazy val cleanThrift = (cleanTask(thriftJavaPath) && cleanTask(thriftRubyPath)) describedAs("Clean thrift generated folder")
  lazy val compileThriftJava = compileThriftAction("java") describedAs("Compile thrift into java")
  lazy val compileThriftRuby = compileThriftAction("rb") describedAs("Compile thrift into ruby")

  /** override to disable auto-compiling of thrift */
  def autoCompileThriftEnabled = true

  lazy val autoCompileThriftJava = task {
    if (autoCompileThriftEnabled) compileThriftJava.run else None
  }

  lazy val autoCompileThriftRuby = task {
    if (autoCompileThriftEnabled) compileThriftRuby.run else None
  }

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / "gen-java" ##)

  override def compileAction = super.compileAction dependsOn(autoCompileThriftJava, autoCompileThriftRuby)

  override def cleanAction = super.cleanAction dependsOn(cleanThrift)
}
