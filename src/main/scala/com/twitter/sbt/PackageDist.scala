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
  val packageDistReleaseBuild =
    SettingKey[Boolean]("package-dist-release-build", "is this a release build")

  /**
   * where to build and stick the dist
   */
  val packageDistDir =
    SettingKey[File]("package-dist-dir", "the directory to package dists into")

  /**
   * the task to actually build the zip file
   */
  val packageDist =
    TaskKey[File]("package-dist", "package a distribution for the current project")

  /**
   * the name of our distribution
   */
  val packageDistName =
    SettingKey[String]("package-dist-name", "name of our distribution")

  /**
   * where to find config files (if any)
   */
  val packageDistConfigPath =
    SettingKey[Option[File]]("package-dist-config-path", "location of config files (if any)")

  /**
   * where to write configs within the zip
   */
  val packageDistConfigOutputPath =
    SettingKey[Option[File]]("package-dist-config-output-path", "location of config output path")

  /**
   * where to find script files (if any)
   */
  val packageDistScriptsPath =
    SettingKey[Option[File]]("package-dist-scripts-path", "location of scripts (if any)")

  /**
   * where to write script files in the zip
   */
  val packageDistScriptsOutputPath =
    SettingKey[Option[File]]("package-dist-scripts-output-path", "location of scripts output path")

  /**
   * the name of our zip
   */
  val packageDistZipName =
    TaskKey[String]("package-dist-zip-name", "name of packaged zip file")

  /**
   * task to clean up the dist directory
   */
  val packageDistClean =
    TaskKey[Unit]("package-dist-clean", "clean distribution artifacts")

  /**
   * task to generate the map of substitutions to perform on scripts as they're copied
   */
  val packageVars =
    TaskKey[Map[String, String]]("package-vars", "build a map of subtitutions for scripts")

  /**
   * task to copy dependent jars from the source folder to dist, doing @VAR@ substitutions along the way
   */
  val packageDistCopyLibs =
    TaskKey[Set[File]]("package-dist-copy-libs", "copy scripts into the package dist folder")

  /**
   * task to copy scripts from the source folder to dist, doing @VAR@ substitutions along the way
   */
  val packageDistCopyScripts =
    TaskKey[Set[File]]("package-dist-copy-scripts", "copy scripts into the package dist folder")

  /**
   * task to copy config files from the source folder to dist
   */
  val packageDistCopyConfig =
    TaskKey[Set[File]]("package-dist-copy-config", "copy config files into the package dist folder")

  // utility to copy a directory tree to a new one
  def copyTree(
    srcOpt: Option[File],
    destOpt: Option[File],
    p: (File => Boolean) = { _ => true }
  ): Seq[(File, File)] = {
    srcOpt.flatMap { src =>
      destOpt.flatMap { dest =>
        val rebaser = Path.rebase(src, dest)
        val allFiles = (PathFinder(src) ***).filter(!_.isDirectory).filter(p).get
        val copySet = allFiles.flatMap { f =>
          rebaser(f) map { rebased =>
            (f, rebased)
          }
        }
        Some(copySet)
      }
    }.getOrElse(Seq())
  }

  val newSettings = Seq(
    exportJars := true,
    // write a classpath entry to the manifest
    packageOptions <+= (dependencyClasspath in Compile, mainClass) map { (cp, main) =>
      val manifestClasspath = cp.files.map(f => "libs/" + f.getName).mkString(" ")
      // not sure why, but Main-Class needs to be set explicitly here.
      val attrs = Seq(("Class-Path", manifestClasspath)) ++ main.map { ("Main-Class", _) }
      Package.ManifestAttributes(attrs: _*)
    },
    packageDistReleaseBuild <<= (version) { v => !(v.toString contains "SNAPSHOT") },
    packageDistName <<= (packageDistReleaseBuild, name, version) { (r, n, v) =>
      if (r) {
        n + "-" + v
      } else {
        n
      }
    },
    packageDistDir <<= (baseDirectory, packageDistName) { (b, n) => b / "dist" / n },
    packageDistConfigPath <<= (baseDirectory) { b => Some(b / "config") },
    packageDistConfigOutputPath <<= (packageDistDir) { d => Some(d / "config") },
    packageDistScriptsPath <<= (baseDirectory) { b => Some(b / "src" / "scripts") },
    packageDistScriptsOutputPath <<= (packageDistDir) { d => Some(d / "scripts") },

    // if release, then name it the version. otherwise the first 8 characters of the sha
    packageDistZipName <<= (
      packageDistReleaseBuild,
      gitProjectSha,
      name,
      version
    ) map { (r, g, n, v) =>
      val revName = g.map(_.substring(0, 8)).getOrElse(v)
      "%s-%s.zip".format(n, if (r) v else revName)
    },

    packageVars <<= (
      dependencyClasspath in Runtime,
      dependencyClasspath in Test,
      exportedProducts in Compile,
      crossPaths,
      name,
      version,
      scalaVersion,
      gitProjectSha
    ) map { (rcp, tcp, exports, crossPaths, name, version, scalaVersion, sha) =>
      val distClasspath = rcp.files.map("${DIST_HOME}/libs/" + _.getName) ++
        exports.files.map("${DIST_HOME}/" + _.getName)
      Map(
        "CLASSPATH" -> rcp.files.mkString(":"),
        "TEST_CLASSPATH" -> tcp.files.mkString(":"),
        "DIST_CLASSPATH" -> distClasspath.mkString(":"),
        "DIST_NAME" -> (if (crossPaths) (name + "_" + scalaVersion) else name),
        "VERSION" -> version,
        "REVISION" -> sha.getOrElse("")
      )
    },

    packageDistCopyScripts <<= (
      packageVars,
      packageDistScriptsPath,
      packageDistScriptsOutputPath
    ) map { (vars, script, scriptOut) =>
      copyTree(script, scriptOut).map { case (source, destination) =>
        destination.getParentFile().mkdirs()
        FileFilter.filter(source, destination, vars)
        List("chmod", "+x", destination.absolutePath.toString) !!;
        destination
      }.toSet
    },

    packageDistCopyConfig <<= (
      packageDistConfigPath,
      packageDistConfigOutputPath
    ) map { (conf, confOut) =>
      // skip anything matching "/target/", which are probably cached pre-compiled config files.
      val fileset = copyTree(conf, confOut, { f => !(f.getPath contains "/target/") })
      IO.copy(fileset)
    },

    packageDistCopyLibs <<= (
      dependencyClasspath in Runtime,
      exportedProducts in Compile,
      packageDistDir
    ) map { (cp, products, dest) =>
      val jarFiles = cp.files.filter(f => !products.files.contains(f))
      val jarDest = dest / "libs"
      jarDest.mkdirs()
      IO.copy(jarFiles.map { f => (f, jarDest / f.getName) })
    },

    // package all the things
    packageDist <<= (
      dependencyClasspath in Runtime,
      exportedProducts in Compile,
      packageDistCopyLibs,
      packageDistCopyScripts,
      packageDistCopyConfig,
      packageDistDir,
      packageDistName,
      packageDistZipName
    ) map { (cp, exp, libs, scripts, configs, dest, distName, zipName) =>
      // copy all our generated "products" (i.e. "the jar")
      val prodCopySet = exp.files.map(p => (p, dest / p.getName))
      val productsToPackage = IO.copy(prodCopySet)
      // build the zip
      val filesToPackage = productsToPackage ++ configs ++ scripts ++ libs
      val zipRebaser = Path.rebase(dest, "")
      val zipFile = dest / zipName
      IO.zip(filesToPackage.map(f => (f, zipRebaser(f).get)), zipFile)
      zipFile
    }
  )
}

