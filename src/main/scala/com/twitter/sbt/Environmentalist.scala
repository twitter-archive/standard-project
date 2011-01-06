package com.twitter.sbt

import scala.collection.jcl

trait Environmentalist {
  val environment = jcl.Map(System.getenv())
}
