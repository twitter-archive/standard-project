package com.twitter.sbt

import sbt._
import Keys._

/**
 * adds a default classpath entry to Compile in doc
 * to get scaladoc to work. So awesome.
 */
object PublishSourcesAndJavadocs extends Plugin {
  val newSettings = Seq(
    // required to get scaladoc to work. yay!
    (dependencyClasspath in Compile in doc) ~= { cp =>
      if (cp.isEmpty) {
        Seq(new java.io.File("doesnotexist")).classpath
      } else {
        cp
      }
    }
  )
}
