package com.twitter.sbt

import _root_.sbt._
import org.apache.ivy.plugins._

trait StrictDependencies extends BasicManagedProject { self: DefaultProject =>
  override def ivySbt: IvySbt = {
    val i = super.ivySbt
    i.withIvy { apacheIvy =>
      val stricty = apacheIvy.getSettings().getConflictManager("strict")
      stricty.asInstanceOf[IvySettingsAware].setSettings(apacheIvy.getSettings())
      apacheIvy.getSettings().setDefaultConflictManager(stricty)
    }
    i
  }
}
