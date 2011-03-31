package com.twitter.sbt

import _root_.sbt._
import _root_.sbt.Process._
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
    if (copyGeneratedDoc() != 0) {
      Some("failed to copy generated doc")
    } else {
      None
    }
  }

  lazy val buildSite = buildSiteTask dependsOn(`package`, packageDocs) describedAs "builds a dope site"

  def gitPublishRepo = Some("http://git.local.twitter.com/blabber.git")

  def publishToGitTask = task {
    gitPublishRepo.flatMap(repo => {
      val tmpdir = System.getProperty("java.io.tmpdir") match {
        case null => "/tmp"
        case t => t
      }
      val tmpLoc = tmpdir + File.separator + "blabber"
      val mySiteLoc = tmpLoc + File.separator + projectName.value
      val siteFullPath = siteOutputPath.asFile.getAbsolutePath
      val localGitRepoExists = if (!(new File(tmpLoc).exists)) {
        val res = ("mkdir -p %s".format(tmpLoc)) !

        if (res == 0) {
          val cloneRes = ((new java.lang.ProcessBuilder("git", "clone", repo, ".") directory new File(tmpLoc) ) !)
          cloneRes == 0
        } else {
          false
        }
      } else {
        true
      }
      if (localGitRepoExists) {
        val res = if ((new File(mySiteLoc)).exists) {
          println("trying to mv %s to %s/%s.%s".format(mySiteLoc, tmpdir, projectName.value, System.currentTimeMillis))
          ("mv -nf %s %s/%s.%s".format(mySiteLoc, tmpdir, projectName.value, System.currentTimeMillis)!)
        } else {
          0
        }
        if (res == 0) {
          // doing this the hard way because we're in a tmp dir
          val copySite = new java.lang.ProcessBuilder("cp",  "-r",  siteFullPath + File.separator, mySiteLoc) directory new File(tmpLoc)
          val gitPull = new java.lang.ProcessBuilder("git",  "pull") directory new File(tmpLoc)
          val gitAdd = new java.lang.ProcessBuilder("git", "add", ".") directory new File(tmpLoc)
          val gitCommit = new java.lang.ProcessBuilder("git", "commit", "--allow-empty", "-m", "%s site update".format(projectName.value)) directory new File(tmpLoc)
          val gitPush = new java.lang.ProcessBuilder("git", "push") directory new File(tmpLoc)

          val gitRes = copySite #&& gitPull #&& gitAdd #&& gitCommit #&& gitPush!

          if (gitRes == 0) {
            None
          } else {
            Some("error publishing to %s, exit code is %d".format(repo, gitRes))
          }
        } else {
          Some("error moving old site directory aside, exit code is %d".format(res))
        }
      } else {
        Some("error cloning repo %s".format(repo))
      }
    })
  }

  lazy val publishToGit = publishToGitTask dependsOn(buildSite) describedAs "publishes to a git repo"

  def publishToGithubTask = task {
    // if gh-pages branch doesn't exist, bail
    val ghPagesSetup: String = ("git branch -lr" #| "grep gh-pages")!!

    if (ghPagesSetup == "") {
      Some("gh-pages branch is not present, not publishing")
    } else {
      val remoteRepo: String = ("git config --get remote.origin.url" !!).trim
      val tmpdir = System.getProperty("java.io.tmpdir") match {
        case null => "/tmp"
        case t => t
      }
      val tmpLoc = "%s/%s".format(tmpdir, projectName.value)
      val siteFullPath = siteOutputPath.asFile.getAbsolutePath

      // set up our working directory, clobbering any existing content
      val res = if ((new File(tmpLoc)).exists) {
        "mv -n %s %s.%s".format(tmpLoc, tmpLoc, System.currentTimeMillis) #&&
        "mkdir -p %s".format(tmpLoc) !
      } else {
        "mkdir -p %s".format(tmpLoc) !
      }

      if (res == 0) {
        // doing this the hard way because we're in a tmp dir
        val gitClone = new java.lang.ProcessBuilder("git", "clone", remoteRepo, "-b", "gh-pages", ".") directory new File(tmpLoc)
        val copySite = new java.lang.ProcessBuilder("cp",  "-r",  siteFullPath + File.separator, ".") directory new File(tmpLoc)
        val gitAdd = new java.lang.ProcessBuilder("git", "add", ".") directory new File(tmpLoc)
        val gitCommit = new java.lang.ProcessBuilder("git", "commit", "--allow-empty", "-m", "site update") directory new File(tmpLoc)
        val gitPush = new java.lang.ProcessBuilder("git", "push", "origin", "gh-pages") directory new File(tmpLoc)

        val gitRes = gitClone #&& copySite #&& gitAdd #&& gitCommit #&& gitPush!

        if (gitRes == 0) {
          None
        } else {
          Some("error publishing to github, exit code is " + gitRes)
        }
      } else {
        Some("error setting up tmp directory, exit code is " + res)
      }
    }
  }

  lazy val publishToGithub = publishToGithubTask dependsOn(buildSite) describedAs "publishes to github"

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

  def copyGeneratedDoc() = {
    scalaDocDir match {
      case Some(docDir) => {
        val doc = siteOutputPath.asFile
        if (!doc.exists) {
          doc.mkdirs()
        }
        "cp -r %s %s".format(docDir.asFile, doc)!
      }
      case None => 0
    }
  }
}
