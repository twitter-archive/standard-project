package com.twitter.sbt

import _root_.sbt._
import java.io.{BufferedReader, FileReader, FileWriter, File}


object FileFilter {
  def filter(sourcePath: Path, destinationPath: Path, filters: Map[String, String]) {
    val in = new BufferedReader(new FileReader(sourcePath.asFile))
    val out = new FileWriter(destinationPath.asFile)
    var line = in.readLine()
    while (line ne null) {
      filters.keys.foreach { token =>
        line = line.replace("@" + token + "@", filters(token))
      }
      out.write(line)
      out.write("\n")
      line = in.readLine()
    }
    in.close()
    out.close()
  }
}
