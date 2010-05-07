package com.twitter.sbt

import java.io.FileReader
import java.util.Properties
import fm.last.ivy.plugins.svnresolver.SvnResolver
import _root_.sbt._


/**
 * Semi-hacky way to publish to a subversion-based maven repository, using ivy-svn.
 */
trait SubversionRepository extends BasicManagedProject { self: DefaultProject =>
  private val prefs = new Properties()
  private val prefsFilename = System.getProperty("user.home") + "/.svnrepo"

  try {
    prefs.load(new FileReader(prefsFilename))
  } catch {
    case e: Exception =>
      log.warn("No .svnrepo file; no svn repo will be configured.")
  }

  lazy val subversionResolver = {
    val repo = prefs.getProperty("repo")
    if (repo ne null) {
      val resolver = new SvnResolver()
      resolver.setName("svn")
      resolver.setRepositoryRoot(repo)
      resolver.addArtifactPattern(prefs.getProperty("pattern", "[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"))

      val username = prefs.getProperty("username")
      if (username ne null) {
        resolver.setUserName(username)
      }
      val password = prefs.getProperty("password")
      if (password ne null) {
        resolver.setUserPassword(password)
      }
      Some(resolver)
    } else {
      None
    }
  }

  override def ivySbt: IvySbt = {
    val i = super.ivySbt
    subversionResolver.foreach { resolver =>
      i.withIvy { _.getSettings().addResolver(resolver) }
    }
    i
  }

  override def publishConfiguration: DefaultPublishConfiguration = {
    subversionResolver match {
      case None =>
        super.publishConfiguration
      case Some(resolver) =>
        new DefaultPublishConfiguration(resolver.getName(), "release", true)
    }
  }
}
