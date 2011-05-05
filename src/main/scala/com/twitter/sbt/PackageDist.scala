package com.twitter.sbt

import _root_.sbt._
import java.io.File

trait PackageDist extends DefaultProject with SourceControlledProject {
  // override me for releases!
  def releaseBuild = false

  // workaround bug in sbt that hides scala-compiler.
  override def filterScalaJars = false

  private[this] def paths(f: BasicScalaProject => PathFinder) =
    Path.lazyPathFinder {
      topologicalSort flatMap {
        case sp: BasicScalaProject => f(sp).get
        case _ => Nil
      }
    }

  // build the executable jar's classpath.
  // (why is it necessary to explicitly remove the target/{classes,resources} paths? hm.)
  def dependentJars = {
    val jars = (
          jarsOfProjectDependencies
      +++ runClasspath
      +++ mainDependencies.scalaJars
      --- paths(_.mainCompilePath)
      --- paths(_.mainResourcesOutputPath)
    )
 
    if (jars.get.find { jar => jar.name.startsWith("scala-library-") }.isDefined) {
      // workaround bug in sbt: if the compiler is explicitly included, don't include 2 versions
      // of the library.
      jars --- jars.filter { jar =>
        jar.absolutePath.contains("/boot/") && jar.name == "scala-library.jar"
      }
    } else {
      jars
    }
  }

  def dependentJarNames = dependentJars.getFiles.map(_.getName).filter(_.endsWith(".jar"))
  override def manifestClassPath = Some(dependentJarNames.map { "libs/" + _ }.mkString(" "))

  def distName = if (releaseBuild) (name + "-" + version) else name
  def distPath = "dist" / distName ##

  def configPath = "config" ##
  def configOutputPath = distPath / "config"

  def scriptsPath = "src" / "scripts" ##
  def scriptsOutputPath = distPath / "scripts"

  def distZipName = {
    val revName = currentRevision.map(_.substring(0, 8)).getOrElse(version)
    "%s-%s.zip".format(name, if (releaseBuild) version else revName)
  }

  // copy scripts.
  val CopyScriptsDescription = "Copies scripts into the dist folder."
  val copyScripts = task {
    val filters = Map(
      "CLASSPATH" -> (publicClasspath +++ mainDependencies.scalaJars).getPaths.mkString(":"),
      "TEST_CLASSPATH" -> testClasspath.getPaths.mkString(":"),
      "DIST_CLASSPATH" -> (dependentJarNames.map { "${DIST_HOME}/libs/" + _ }.mkString(":") +
        ":${DIST_HOME}/" + defaultJarName),
      "DIST_NAME" -> name,
      "VERSION" -> version.toString
    )

    scriptsOutputPath.asFile.mkdirs()
    (scriptsPath ***).filter { !_.isDirectory }.get.foreach { path =>
      val dest = Path.fromString(scriptsOutputPath, path.relativePath)
      new File(dest.absolutePath.toString).getParentFile().mkdirs()
      FileFilter.filter(path, dest, filters)
      Runtime.getRuntime().exec(List("chmod", "+x", dest.absolutePath.toString).toArray).waitFor()
    }
    None
  } named("copy-scripts") dependsOn(`compile`) describedAs CopyScriptsDescription

  /**
   * copy into dist:
   * - packaged jar
   * - pom file for export
   * - dependent libs
   * - config files
   * - scripts
   */
  def packageDistTask = interactiveTask {
    distPath.asFile.mkdirs()
    (distPath / "libs").asFile.mkdirs()
    configOutputPath.asFile.mkdirs()

    FileUtilities.copyFlat(List(jarPath), distPath, log).left.toOption orElse
      FileUtilities.copyFlat(dependentJars.get, distPath / "libs", log).left.toOption orElse
      FileUtilities.copy((configPath ***).get, configOutputPath, log).left.toOption orElse
      FileUtilities.copy(((outputPath ##) ** "*.pom").get, distPath, log).left.toOption orElse
      FileUtilities.zip((("dist" / distName) ##).get, "dist" / distZipName, true, log)
  }

  val PackageDistDescription = "Creates a deployable zip file with dependencies, config, and scripts."
  lazy val packageDist = packageDistTask dependsOn(`package`, makePom, copyScripts) describedAs PackageDistDescription

  val cleanDist = cleanTask("dist" ##) describedAs("Erase any packaged distributions.")
  override def cleanAction = super.cleanAction dependsOn(cleanDist)
}
