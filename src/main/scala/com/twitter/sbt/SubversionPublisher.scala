package com.twitter.sbt

import sbt._
import Keys._

import fm.last.ivy.plugins.svnresolver.SvnResolver

import java.io.{File, FileReader}
import java.util.Properties

/**
 * support subversion resolvers
 */
object SubversionPublisher extends Plugin {
  /**
   * a file that contains your username and password
   */
  val subversionPrefsFile = SettingKey[Option[File]]("subversion-prefs-file", "preferences file for subversion publisher")
  /**
   * the repo to publish to
   */
  val subversionRepository = SettingKey[Option[String]]("subversion-repository", "subversion repo to publish artifacts to")
  /**
   * svn user if you want to override another way than the prefs file
   */
  val subversionUser = SettingKey[Option[String]]("subversion-user", "subversion username. usually read from subversion-prefs-file")
  /**
   * svn password if you want to override another way than the prefs file
   */
  val subversionPassword = SettingKey[Option[String]]("subversion-password", "subversion password. usually read from subversion-prefs-file")
  /**
   * the loaded prefs file, or your override
   */
  val subversionProperties = SettingKey[Option[Properties]]("subversion-properties", "loaded subversion preferences")
  /**
   * the svn url to publish to
   */
  val subversionPublishTo = SettingKey[Option[Resolver]]("subversion-publish-to", "subversion resolver to publish to")

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
    subversionUser <<= (subversionProperties) { propsOpt => mapPropOpt(propsOpt, "username") },
    subversionPassword <<= (subversionProperties) { propsOpt => mapPropOpt(propsOpt, "password") },
    // make our resolver
    subversionPublishTo <<= (subversionUser, subversionPassword, subversionRepository, subversionProperties) {
      (userOpt, passwordOpt, repoOpt, svnProps) =>
      val resolverOpt: Option[Resolver] = userOpt flatMap { username =>
        passwordOpt flatMap { password =>
          repoOpt flatMap {repo =>
            svnProps map { prefs =>
              val resolver = new SvnResolver()
              resolver.setName("svn")
              resolver.setRepositoryRoot(repo)
              resolver.addArtifactPattern(prefs.getProperty("pattern", Resolver.mavenStyleBasePattern)
              resolver.setM2compatible(java.lang.Boolean.parseBoolean(prefs.getProperty("m2Compatible", "true")))
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
    publishTo <<= subversionPublishTo
  )
}
