package com.twitter.sbt

import sbt._
import Keys._

/**
 * replaces the resolver named "local" with a maven style one.
 * This local one is defined in DefaultRepos
 */
object PublishLocalWithMavenStyleBasePattern extends Plugin {
  import DefaultRepos._
  val newSettings: Seq[Setting[_]] = Seq(
    publishMavenStyle := true,
    externalResolvers <<= (externalResolvers, defaultReposLocalPublishResolver) map { (e, lOpt) =>
      lOpt match {
        case Some(l) => {
          val filtered = e.filter(r => r.name != "local")
	  val pList = ("${ivy.home}/local/" + Resolver.localBasePattern) :: Nil
	  val oldDefault = FileRepository("sbt-local", Resolver.defaultFileConfiguration, Patterns(pList, pList, false))
          Seq(l, oldDefault) ++ filtered
        }
        case _ => e
      }
    }
  )
}
