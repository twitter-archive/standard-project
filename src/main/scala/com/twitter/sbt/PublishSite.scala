package com.twitter.sbt

import _root_.sbt._
import java.io._
import freemarker.template.{Configuration => FreeConfig}
import freemarker.cache.StringTemplateLoader
import com.petebevin.markdown._
import scala.io._

trait PublishSite extends DefaultProject {
  /** where we'll stick our generated web site */
  def siteOutputPath = outputRootPath / "site"
  /** where our generated doc goes */
  def scalaDocDir: Option[Path] = Some(docPath)
  /** make the directory for our site */
  def buildSiteDir() = siteOutputPath.asFile.mkdir
  /** is our readme in markdown format? */
  def isReadmeMarkdown = true
  /** the filename of our readme */
  def readmeFileName: Option[String] = None

  // Jank. But getting a template in a plugin readable from the project seems hard
  def indexTemplate = """
<html>
<head>
<title>${projectName}</title>
</head>
<body>
<#if readme??>
<h1>README</h1>
${readme}
</#if>

<#if scaladoc??>
<h1>DOC</h1>
<a href="doc/main/api/index.html">ScalaDoc</a>
</#if>
</body>
</html>
  """

  def buildSiteTask = task {
    val cfg = new FreeConfig()
    val templateLoader = new StringTemplateLoader()
    templateLoader.putTemplate("index", indexTemplate)
    cfg.setTemplateLoader(templateLoader)

    buildSiteDir()
    buildIndex(cfg)
    copyGeneratedDoc()
    None
  }

  lazy val buildSite = buildSiteTask dependsOn(`package`, packageDocs) describedAs "builds a dope site"

  def buildIndex(cfg: FreeConfig) = {
    val oldIndex = (siteOutputPath / "index.html").asFile
    if (oldIndex.exists) {
      oldIndex.delete
    }
    val index = (siteOutputPath / "index.html").asFile
    val writer = new BufferedWriter(new FileWriter(index))
    val model = new java.util.HashMap[Any, Any]()
    model.put("projectName", projectName.value)
    scalaDocDir.foreach(model.put("scaladoc",  _))
    findReadme.map(processReadme(_)).foreach(model.put("readme", _))
    val template = cfg.getTemplate("index")
    template.process(model, writer)
    writer.close()
  }

  def findReadme() = {
    readmeFileName match {
      case Some(s) => Some(s)
      case None => {
        val basePath = Path.fromFile(outputRootPath + File.separator + "..")
        List(basePath / "README", basePath / "README.md").find(candidate => candidate.asFile.exists) map {_.toString}
      }
    }
  }
  def processReadme(fileName: String): String = {
    val source = Source.fromFile(fileName)
    val text = source.mkString
    if (isReadmeMarkdown) {
      val processor = new MarkdownProcessor()
      processor.markdown(text)
    } else {
      text
    }
  }

  def recursiveCopy(src: File, tgt: File): Unit = {
    if (!tgt.exists) {
      tgt.mkdirs()
    }
    src.listFiles.foreach(f => {
      if (f.getName() == "." || f.getName() == "..") {
        // noop
      } else if (f.isDirectory) {
        recursiveCopy(new File(src.getAbsolutePath() + File.separator + f.getName()),
                      new File(tgt.getAbsolutePath() + File.separator + f.getName()))
      } else {
        val out = new File(tgt.getAbsolutePath() + File.separator + f.getName())
        val from = new FileInputStream(f)
        val to = new FileOutputStream(out)
        var c = from.read()
        while(c != -1) {
          to.write(c)
          c = from.read()
        }
        from.close()
        to.close()
      }
    })
  }

  def copyGeneratedDoc() = {
    scalaDocDir.foreach(docDir => {
      val doc = (siteOutputPath / "doc").asFile
      if (!doc.exists) {
        doc.mkdirs()
      }
      recursiveCopy(docDir.asFile, doc)
    })
  }
}
