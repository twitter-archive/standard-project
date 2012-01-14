package com.twitter.sbt

import sbt._
import Keys._

/**
 * build a twitter style packgae containing the packaged jar, all its deps,
 * configs, scripts, etc.
 */
object PackageDist extends Plugin {
  // only works for git projects
  import GitProject._
  
  /**
   * flag for determining whether we name this with a version or a sha
   */
  val packageDistReleaseBuild = SettingKey[Boolean]("package-dist-release-build", "is this a release build")
  /**
   * where to build and stick the dist
   */
  val packageDistDir = SettingKey[File]("package-dist-dir", "the directory to package dists into")
  /**
   * the task to actually build the zip file
   */
  val packageDist = TaskKey[Unit]("package-dist", "package a distribution for the current project")
  /**
   * the name of our distribution
   */
  val packageDistName = SettingKey[String]("package-dist-name", "name of our distribution")
  /**
   * where to find config files (if any)
   */
  val packageDistConfigPath = SettingKey[Option[File]]("package-dist-config-path", "location of config files (if any)")
  /**
   * where to write configs within the zip
   */
  val packageDistConfigOutputPath = SettingKey[Option[File]]("package-dist-config-output-path", "location of config output path")
  /**
   * where to find script files (if any)
   */
  val packageDistScriptsPath = SettingKey[Option[File]]("package-dist-scripts-path", "location of scripts (if any)")
  /**
   * where to write script files in the zip
   */
  val packageDistScriptsOutputPath = SettingKey[Option[File]]("package-dist-scripts-output-path", "location of scripts output path")
  /**
   * the name of our zip
   */
  val packageDistZipName = TaskKey[String]("package-dist-zip-name", "name of packaged zip file")
  /**
   * task to clean up the dist directory
   */
  val packageDistClean = TaskKey[Unit]("package-dist-clean", "clean distribution artifacts")

  val newSettings = Seq(
    exportJars := true,
    // write a classpath entry to the manifest
    packageOptions <+= (dependencyClasspath in Compile) map { cp =>
      val manifestClasspath = cp.map(_.data).map(f => "libs/" + f.getName).mkString(" ")
      Package.ManifestAttributes(("Class-Path", manifestClasspath))
    },
    packageDistDir <<= (baseDirectory) { b => b / "dist"},
    packageDistReleaseBuild := false,
    packageDistName <<= (packageDistReleaseBuild, name, version) { (r, n, v) =>
      if (r) {
        n + "-" + v
      } else {
        n
      }
    },
    packageDistConfigPath <<= (baseDirectory) { b => Some(b / "config") },
    packageDistConfigOutputPath <<= (packageDistDir) { d => Some(d / "config") },
    packageDistScriptsPath <<= (baseDirectory) {b => Some(b / "src" / "scripts") },
    packageDistScriptsOutputPath <<= (packageDistDir) { d => Some(d / "scripts") },
    // if release, then name it the version. otherwise the first 8 characters of the sha
    packageDistZipName <<= (packageDistReleaseBuild, gitProjectSha, name, version) map { (r, g, n, v) =>
      val revName = g.map(_.substring(0, 8)).getOrElse(v)
      "%s-%s.zip".format(n, if (r) v else revName)                 
    },
    // package all the things
    packageDist <<= (dependencyClasspath in Runtime,
                     exportedProducts in Compile,
                     packageDistDir,
                     packageDistConfigPath,
                     packageDistConfigOutputPath,
                     packageDistScriptsPath,
                     packageDistScriptsOutputPath,
                     packageDistZipName) map { (cp, exp, dest, conf, confOut, script, scriptOut, zipName) =>
      // build up lib directory
      val jarFiles = cp.map(_.data).filter(f => !exp.map(_.data).contains(f))
      val jarDest = dest / "libs"
      if (!jarDest.exists) {
        jarDest.mkdirs()
      }
      val copySet = jarFiles.map { f =>
        (f, jarDest / f.getName)
      }
      IO.copy(copySet)

      // utility to copy a directory tree to a new one
      def copyDirs(srcOpt: Option[File], destOpt: Option[File]) {
        srcOpt.foreach { src =>
          destOpt.foreach { dest =>
            val rebaser = Path.rebase(src, dest)
            val allFiles = (PathFinder(src) ***).filter(!_.isDirectory)get
            val copySet = allFiles.flatMap { f =>
              rebaser(f) map { rebased =>
                (f, rebased)
              }
            }
            IO.copy(copySet)                         
          }
        }
      }
      // copy all of scripts and confs (rebased to dist directory)
      copyDirs(conf, confOut)
      copyDirs(script, scriptOut)
      // copy all our generated "products" (i.e. "the jar")
      val prodCopySet = exp.map(p => (p.data, dest / p.data.getName))
      val productsToPackage = prodCopySet.map(_._2)
      IO.copy(prodCopySet)
      // build the zip
      val filesToPackage = productsToPackage ++
         confOut.map { confOut => (PathFinder(confOut) ***).get}.getOrElse(Seq()) ++
      scriptOut.map { scriptOut => (PathFinder(scriptOut) ***).get}.getOrElse(Seq()) ++
         (PathFinder(dest / "libs") ***).get
      val zipRebaser = Path.rebase(dest, "")
      IO.zip(filesToPackage.map(f => (f, zipRebaser(f).get)), dest / zipName)
    }
  )  
}
