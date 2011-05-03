package com.twitter.sbt

import _root_.sbt._
import java.io.File

/**
 * Use a ramdisk as the build target folder if one is specified in `SBT_RAMDISK_ROOT`.
 */
trait Ramdiskable extends DefaultProject with Environmentalist {
  private val ramdiskRoot = environment.get("SBT_RAMDISK_ROOT")
  private val ramdiskTargetName = "target-ramdisk"

  for (ramdiskRoot <- ramdiskRoot) {
    val ramdiskPath = new File("%s/%s".format(ramdiskRoot, name))
    log.info("Compiling to ramdisk at %s".format(ramdiskPath))

    val target = new File(ramdiskTargetName)
    val canonicalPath = target.getCanonicalPath
    val absolutePath = target.getAbsolutePath

    if (target.exists && canonicalPath != ramdiskRoot) {
      if (target.isFile || absolutePath != canonicalPath) {
        log.info("Deleting existing symlink at %s".format(target))
        target.delete()
      } else {
        log.info("Removing existing directory at %s".format(target))
        FileUtilities.clean(Path.fromFile(target), log)
      }
    }

    // Make symlink.
    if (!target.exists) {
      import Process._
      log.info("Creating ramdisk build symlink %s".format(ramdiskPath))
      ramdiskPath.mkdirs()
      (execTask { "ln -s %s %s".format(ramdiskPath, ramdiskTargetName) }).run
    }
  }

  override def outputRootPath =
    if (ramdiskRoot.isDefined)
      "target-ramdisk": Path
    else
      super.outputRootPath
}
