package com.twitter.sbt

import sbt._
import Keys._

import fm.last.ivy.plugins.svnresolver.SvnResolver

import java.io.{File, FileReader}
import java.util.Properties

/**
 * Support publishing to a subversion repo, using ivy-svn.
 * It's a dirty job but someone's gotta do it.
 */
object SubversionPublisher extends Plugin {
  val subversionPrefsFile = SettingKey[Option[File]](
    "subversion-prefs-file",
    "preferences file for subversion publisher (contating username and password)"
  )

  val subversionRepository = SettingKey[Option[String]](
    "subversion-repository",
    "subversion repo to publish artifacts to"
  )

  val subversionUsername = SettingKey[Option[String]](
    "subversion-username",
    "login username for the subversion repo (read from subversion-prefs-file by default)"
  )

  val subversionPassword = SettingKey[Option[String]](
    "subversion-password",
    "login password for the subversion repo (read from subversion-prefs-file by default)"
  )

  val subversionProperties = SettingKey[Option[Properties]](
    "subversion-properties",
    "properties loaded from subversion-prefs-file"
  )

  val subversionResolver = SettingKey[Option[Resolver]](
    "subversion-resolver",
    "ivy resolver object for publishing (usually built by this plugin)"
  )

  /**
   * given an optional properties object and a key, return a value if it exists
   */
  def mapPropOpt(propOpt: Option[Properties], key: String): Option[String] = {
    propOpt match {
      case Some(prefs) => {
        val rv = prefs.getProperty(key)
        if (rv ne null) {
          Some(rv)
        } else {
          None
        }
      }
      case _ => None
    }
  }

  val newSettings = Seq(
    // defaults to ~/.svnrepo
    subversionPrefsFile := {
      Some(new File(System.getProperty("user.home") + "/.svnrepo"))
    },
    // load the file
    subversionProperties <<= (subversionPrefsFile) { prefsOpt =>
      try {
        prefsOpt match {
          case Some(prefs) => {
            val prefProps = new Properties()
            prefProps.load(new FileReader(prefs))
            Some(prefProps)
          }
          case _ => None
        }
      } catch {
        case e: Exception => {
          println("No .svnrepo file; no svn repo will be configured.")
          None
        }
      }
    },
    subversionRepository := None,
    subversionUsername <<= (subversionProperties) { propsOpt => mapPropOpt(propsOpt, "username") },
    subversionPassword <<= (subversionProperties) { propsOpt => mapPropOpt(propsOpt, "password") },

    // make our resolver
    subversionResolver <<= (
      subversionUsername,
      subversionPassword,
      subversionRepository,
      subversionProperties
    ) { (userOpt, passwordOpt, repoOpt, svnProps) =>
      val resolverOpt: Option[Resolver] = userOpt flatMap { username =>
        passwordOpt flatMap { password =>
          repoOpt flatMap {repo =>
            svnProps map { prefs =>
              val resolver = new SvnResolver()
              resolver.setName("svn")
              resolver.setRepositoryRoot(repo)
              resolver.addArtifactPattern(prefs.getProperty("pattern", Resolver.mavenStyleBasePattern))
              resolver.setM2compatible(prefs.getProperty("m2Compatible", "true") == "true")
              resolver.setUserName(username)
              resolver.setUserPassword(password)
              resolver.setBinaryDiff("true")
              resolver.setBinaryDiffFolderName(".upload")
              resolver.setCleanupPublishFolder("true")
              new RawRepository(resolver)
            }
          }
        }
      }
      resolverOpt
    },

    publishTo <<= subversionResolver
  )
}
