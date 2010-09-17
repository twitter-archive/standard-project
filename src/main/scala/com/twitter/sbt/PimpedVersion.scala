package com.twitter.sbt

import _root_.sbt.{Version, BasicVersion}

object pimpedversion {
  class PimpedVersion(wrapped: BasicVersion) {
    private def increment(i: Option[Int]) = Some(i.getOrElse(0) + 1)

    // these methods do the wrong thing in BasicVersion. :(
    def incMicro() = BasicVersion(wrapped.major, wrapped.minor.orElse(Some(0)), increment(wrapped.micro), wrapped.extra)
    def incMinor() = BasicVersion(wrapped.major, increment(wrapped.minor), wrapped.micro.map { _ => 0 }, wrapped.extra)
    def incMajor() = BasicVersion(wrapped.major + 1, wrapped.minor.map { _ => 0 }, wrapped.micro.map { _ => 0 }, wrapped.extra)

    def stripSnapshot() = {
      val stripped = wrapped.extra.map(_.replaceAll("""-?SNAPSHOT""", "")).flatMap { s =>
        if ( s.length > 0 ) Some(s) else None
      }

      BasicVersion(wrapped.major, wrapped.minor, wrapped.micro, stripped)
    }

    def addSnapshot() = {
      val unstripped = stripSnapshot().extra.map( _ + "-SNAPSHOT").orElse(Some("SNAPSHOT"))
      BasicVersion(wrapped.major, wrapped.minor, wrapped.micro, unstripped)
    }
  }

  implicit def pimpVersion(wrapped: Version) = new PimpedVersion(wrapped.asInstanceOf[BasicVersion])
}
