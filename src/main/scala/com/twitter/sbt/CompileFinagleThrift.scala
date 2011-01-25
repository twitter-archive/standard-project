package com.twitter.sbt

import java.io.{File, FileOutputStream}

import _root_.sbt._

trait CompileFinagleThrift
  extends DefaultProject
  with CompileThrift
{
  private[this] val _thriftBin = {
    // TODO: we don't discriminate between versions here (which we
    // need to..).
    val binPath = System.getProperty("os.name") match {
      case "Mac OS X" => "thrift.osx10.6"
      case "Linux" => "thrift.linux"
      case unknown => throw new Exception(
        "No thrift binary for %s, talk to marius@twitter.com".format(unknown))
    }

    val stream = getClass.getResourceAsStream("/thrift/%s".format(binPath))
    val file = File.createTempFile("thrift", "scala")
    file.deleteOnExit()
    val fos = new FileOutputStream(file)

    var byte = stream.read()
    while (byte != -1) {
      fos.write(byte)        
      byte = stream.read()
    }

    import Process._
    val path = file.getAbsolutePath()
    (execTask { "chmod 0500 %s".format(path) }).run
    path
  }

  override def thriftBin = _thriftBin
}
