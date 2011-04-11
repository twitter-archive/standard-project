package com.twitter.sbt

/**
 * The ProjectCache allows for managing a cache of instantiated
 * projects across subprojects.
 */

import scala.collection.mutable.HashMap
import _root_.sbt._

trait ProjectCache extends BasicManagedProject {
  private[this] var _projectCacheStore: HashMap[String, Project] = null

  def projectCacheStore: HashMap[String, Project] =
    info.parent match {
      case None =>
        if (_projectCacheStore == null)
          _projectCacheStore = new HashMap[String, Project]
        _projectCacheStore

      case Some(parent) =>
        val m = try {
          parent.getClass.getMethod("projectCacheStore")
        } catch {
          case e: NoSuchMethodException =>
            log.error("Parent project is invalid!")
            throw e
        }
         
        m.invoke(parent).asInstanceOf[HashMap[String, Project]]
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
