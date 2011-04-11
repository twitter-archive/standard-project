package com.twitter.sbt

/**
 * Support for intransitive dependence analysis in SBT. This uses the
 * "secret" sbt.intransitive options, plus a hack subclass of
 * CompileConditional to override dirty external dependencies. This is
 * necessary in order to build subprojects with intransitive
 * dependence analysis.
 */

import _root_.sbt._
import xsbt.AnalyzingCompiler
import java.io.File

trait IntransitiveCompiles extends DefaultProject with Environmentalist {
  environment.get("SBT_INTRANSITIVE") map {
    case "0" | "false" => "false"
    case _ => "true"
  } foreach { System.setProperty("sbt.intransitive", _) }

  protected def isIntransitive = java.lang.Boolean.getBoolean("sbt.intransitive")

  class IntransitiveCompileConditional(
      config: CompileConfiguration,
      compiler: AnalyzingCompiler)
    extends CompileConditional(config, compiler)
  {
    // This is the meat of the trick: we set last modified time to 0
    // for all external dependencies.
    override protected def externalInfo(externals: Iterable[File]) =
      externals map { e => (e, ExternalInfo(true, 0L)) }
  }

	lazy val intransitiveMainCompileConditional =
    new IntransitiveCompileConditional(
      mainCompileConfiguration, buildCompiler)

	lazy val intransitiveTestCompileConditional =
    new IntransitiveCompileConditional(
      testCompileConfiguration, buildCompiler)

  private def defaultMainCompileConditional =
    if (isIntransitive) intransitiveMainCompileConditional
    else                mainCompileConditional

  private def defaultTestCompileConditional =
    if (isIntransitive) intransitiveTestCompileConditional
    else                testCompileConditional

  override def compileAction = task {
    defaultMainCompileConditional.run
  } .dependsOn(super.compileAction.dependencies: _*)
    .describedAs("compile the project [possibly intransitively]")

  override def testCompileAction = task {
    defaultTestCompileConditional.run
  } .dependsOn(super.testCompileAction.dependencies: _*)
    .describedAs("test-compile the project [possibly intransitively]")

  override protected def defaultTestTask(testOptions: => Seq[TestOption]) =
		testTask(
      testFrameworks,
      testClasspath,
      defaultTestCompileConditional.analysis,
      testOptions).dependsOn(super.defaultTestTask(testOptions).dependencies: _*)

  override def cleanOptions: Seq[CleanOption] =
    ClearAnalysis(intransitiveMainCompileConditional.analysis) ::
    ClearAnalysis(intransitiveTestCompileConditional.analysis) ::
    super.cleanOptions.toList

  lazy val queryTransitive = task {
    val onOrOff = if (isIntransitive) "OFF" else "ON"
    log.info("transitivity is " + onOrOff)
    None
  }

  lazy val transitive = task {
    System.setProperty("sbt.intransitive", "false")
    log.info("project builds are now transitive")
    None
  }

  lazy val intransitive = task {
    System.setProperty("sbt.intransitive", "true")
    log.info("project builds are now intransitive")
    None
  }
}

