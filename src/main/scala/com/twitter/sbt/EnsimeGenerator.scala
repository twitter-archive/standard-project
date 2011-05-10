package com.twitter.sbt

import _root_.sbt._
import java.io.{FileWriter, File}

trait EnsimeGenerator extends DefaultProject {
  /**
   * A blacklist for dependencies that cause problems. Add the name of
   * any dependency that doesn't work correctly with ensime.
   * (e.g. "standard-project")
   */
  val ensimeBlacklist: List[String] = Nil

  /**
   * Generates a .ensime file for the project and its dependencies.
   */
  lazy val generateEnsime = task { _ => interactiveTask {
    val file = new java.io.File(info.projectDirectory, ".ensime")
    val out = new java.io.PrintWriter(new java.io.FileWriter(file))

    out.println("(")
    out.println(":project-package \"com.twitter\"")
    out.println(":compile-jars (")

    testClasspath.get foreach {
      case path if path.absolutePath.endsWith(".jar") =>
        out.println("  \"%s\"".format(path.absolutePath))
      case _ => ()
    }

    out.println("  )")
    out.println(":sources (")

    Seq("mainSourceRoots", "testSourceRoots") foreach { methodName =>
      projectClosure foreach { project =>
        try {
          if (!ensimeBlacklist.contains(project.name) || methodName != "testSourceRoots") {
            val m = project.getClass.getMethod(methodName)
            val finder = m.invoke(project).asInstanceOf[PathFinder]
            finder.get foreach { path =>
              out.println("      \"%s\"".format(path.absolutePath))
            }
          }
        } catch { case _ => () }
      }
    }
    out.println("  )")
    out.println(")")
    out.close()

    None
  } dependsOn(compile, testCompile) } describedAs("Generate a .ensime file for all projects together.")
}
