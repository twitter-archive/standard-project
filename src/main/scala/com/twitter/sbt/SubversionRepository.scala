package com.twitter.sbt

import java.io.FileReader
import java.util.Properties
import fm.last.ivy.plugins.svnresolver.SvnResolver
import _root_.sbt._


/**
 * Semi-hacky way to publish to a subversion-based maven repository, using ivy-svn.
 */
trait SubversionRepository { self: DefaultProject =>
  private val prefs = new Properties()
  private val prefsFilename = System.getProperty("user.home") + "/.svnrepo"

  try {
    prefs.load(new FileReader(prefsFilename))
  } catch {
    case e: Exception =>
      log.warn("No .svnrepo file; no svn repo will be configured.")
  }

  def subversionResolver = {
    val repo = prefs.getProperty("repo")
    if (repo ne null) {
      val resolver = new SvnResolver()
      resolver.setName("svn")
      resolver.setRepositoryRoot(repo)
      resolver.addArtifactPattern(prefs.getProperty("pattern", "[organisation]/[module]/[revision]/[artifact].[ext]"))

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

  def publishSvnTask = ivyTask {
    val module = publishIvyModule
    val sourcePatterns: Iterable[String] = {
      val pathPatterns =
              (outputPath / "[artifact]-[revision]-[type](-[classifier]).[ext]") ::
              (outputPath / "[artifact]-[revision](-[classifier]).[ext]") ::
              Nil
      pathPatterns.map(_.relativePath)
    }

    subversionResolver match {
      case None =>
        Some("Can't publish to subversion without a repo!")
      case Some(resolver) =>
        module.withModule { (ivy, md, default) => ivy.getSettings().addResolver(resolver) }
        IvyActions.publish(module, resolver.getName(), sourcePatterns, None, None)
        None
    }
  }

  def publishSvnAction = {
    publishSvnTask dependsOn(deliverLocal, makePom)
  }
  lazy val publishSvn = publishSvnAction
}