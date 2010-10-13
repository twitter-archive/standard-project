package com.twitter.sbt

import _root_.sbt._
import org.apache.ivy.plugins._
import org.apache.ivy.core.resolve._
import org.apache.ivy.plugins.conflict._
import org.apache.ivy.core.settings.IvySettings
import java.util.Collection

class LatestCompatibleWarningsManager extends AbstractConflictManager {
  val loose = new LatestConflictManager
  val strict = new LatestCompatibleConflictManager

  override def setSettings(ivySettings: IvySettings) = {
    loose.setSettings(ivySettings)
    strict.setSettings(ivySettings)
    super.setSettings(ivySettings)
  }

  override def resolveConflicts(parent: IvyNode, conflicts: Collection[_]): Collection[_] = {
    try {
      strict.resolveConflicts(parent, conflicts)
    } catch {
      case e: StrictConflictException => {
        print("Warning: " + e + "\n")
        loose.resolveConflicts(parent, conflicts)
      }
    }
  }
}

trait NoisyDependencies extends BasicManagedProject { self: DefaultProject =>

  override def ivySbt: IvySbt = {
    val i = super.ivySbt
    i.withIvy { apacheIvy =>
      val stricty = new LatestCompatibleWarningsManager
      stricty.asInstanceOf[IvySettingsAware].setSettings(apacheIvy.getSettings())
      apacheIvy.getSettings().setDefaultConflictManager(stricty)
    }
    i
  }
}
