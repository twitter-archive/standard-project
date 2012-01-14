package com.twitter.sbt

import scala.collection.mutable.Map
import scala.collection.JavaConversions._

/**
 * make a nice Scala map of the environment
 */
trait Environmentalist {
  /**
   * a Scala map of System.getenv()
   */
  val environment: Map[String, String] = System.getenv()
}
