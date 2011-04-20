package com.twitter.sbt

import java.io.FileReader
import java.util.Properties
import fm.last.ivy.plugins.svnresolver.SvnResolver
import _root_.sbt._

/**
 * Semi-hacky way to publish to a subversion-based maven repository, using ivy-svn.
 */
trait SubversionPublisher extends BasicManagedProject {
  private val prefs = new Properties()
  val prefsFilename = System.getProperty("user.home") + "/.svnrepo"

  // override me to publish to subversion.
  def subversionRepository: Option[String] = info.parent match {
    case Some(parent: SubversionPublisher) => parent.subversionRepository
    case _ => None
  }

  private val loaded = try {
    prefs.load(new FileReader(prefsFilename))
    true
  } catch {
    case e: Exception =>
      log.warn("No .svnrepo file; no svn repo will be configured.")
      false
  }

  lazy val subversionResolver = {
    if (loaded) {
      subversionRepository.map { repo =>
        val resolver = new SvnResolver()
        resolver.setName("svn")
        resolver.setRepositoryRoot(repo)
        resolver.addArtifactPattern(prefs.getProperty("pattern", "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"))
        resolver.setM2compatible(java.lang.Boolean.parseBoolean(prefs.getProperty("m2Compatible", "true")))

        val username = prefs.getProperty("username")
        if (username ne null) {
          resolver.setUserName(username)
        }
        val password = prefs.getProperty("password")
        if (password eq null) {
          // Try to prompt the user for a password.
          val console = System.console
          if (console ne null) {
            // This is super janky -- it seems that sbt hoses the
            // console in a way so that it isn't line-buffered anymore,
            // or for some other reason causes Console.readPassword to
            // give us only one character at time.
            def readPassword: Stream[Char] = {
              val chars = console.readPassword("SVN repository password: ")
              if ((chars eq null) || chars.isEmpty)
                Stream.empty
              else
                Stream.concat(Stream.fromIterator(chars.elements), readPassword)
            }

            resolver.setUserPassword(new String(readPassword.toArray))
          }
        } else {
          resolver.setUserPassword(password)
        }
        resolver.setBinaryDiff("true")
        resolver.setBinaryDiffFolderName(".upload")
        resolver.setCleanupPublishFolder("true")
        resolver
      }
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
