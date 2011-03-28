package com.twitter.sbt

/**
 * The ProjectCache allows for managing a cache of instantiated
 * projects across subprojects.
 */

import scala.collection.mutable.HashMap
import _root_.sbt._

trait ProjectCache extends BasicManagedProject {
  private[this] var _projectCacheStore: HashMap[String, Project] = null

  def projectCacheStore: HashMap[String, Project] = {
    if (_projectCacheStore eq null)
      _projectCacheStore = new HashMap[String, Project]

    _projectCacheStore
  }

  protected def setProjectCacheStoreInProject(p: Project, store: HashMap[String, Project]) = {
    val m = p.getClass.getDeclaredMethod(
      "setProjectCacheStore", classOf[HashMap[String, Project]])
    if (m ne null) {
			m.invoke(p, store)
		} else {
      log.error("project %s is not a ProjectCache project".format(p.name))
      System.exit(1)
    }
  }

  def setProjectCacheStore(store: HashMap[String, Project]) {
    _projectCacheStore = store

    subProjects foreach { case (_, p) =>
			setProjectCacheStoreInProject(p, store)
    }
  }

  protected def projectCache(key: String)(make: => Option[Project]) = {
    projectCacheStore.get(key) match {
      case someProject@Some(_) => someProject
      case None =>
        val made = make
        made foreach { project =>
          projectCacheStore(key) = project
        }

        made
    }
  }
}
