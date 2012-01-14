package com.twitter.sbt

import sbt._
import Keys._
/**
 * various tasks for working with git-based projects
 */
object GitProject extends Plugin {
  /**
   * returns true if this is a git repo
   */
  val gitIsRepository = TaskKey[Boolean]("git-is-repository", "a task for determining if this is a git project")
  /**
   * returns the current git sha (if this is a repo)
   */
  val gitProjectSha = TaskKey[Option[String]]("git-project-sha", "the SHA of the current project")
  /**
   * how many commits to return with last-commits
   */
  val gitLastCommitsCount = SettingKey[Int]("git-last-commits-count", "the number of commits to report from git-last-commits")
  /**
   * the last n commits, where n is determined by git-last-commits-count
   */
  val gitLastCommits = TaskKey[Option[Seq[String]]]("git-last-commits", "the latest commits to the project")
  /**
   * the current branch name
   */
  val gitBranchName = TaskKey[Option[String]]("git-branch-name", "the name of the git branch")
  /**
   * get a default commit message for the project (usually "commiting release <n>")
   */
  val gitCommitMessage = TaskKey[String]("git-commit-message", "get a commit message for the project")
  /**
   * add and commit the current project
   */
  val gitCommit = TaskKey[Int]("git-commit", "commit the current project")
  /**
   * tag the current version
   */
  val gitTag = TaskKey[Int]("git-tag", "tag the project with the current version")
  /**
   * generates a tag for the current project
   */
  val gitTagName = TaskKey[String]("git-tag-name", "a task to define a tag name given the current project")

  /**
   * run f if isRepo is true
   */
  def ifRepo[T](isRepo: Boolean)(f: => T): Option[T] = {
    if (isRepo) {
      Some(f)
    } else {
      None
    }
  }

  val gitSettings = Seq(
    gitIsRepository := { ("git status" ! NullLogger) == 0 },
    gitProjectSha <<= (gitIsRepository) map { isRepo =>
      ifRepo(isRepo) {
        ("git rev-parse HEAD" !!).trim
      }
    },
    gitLastCommitsCount := 10,
    gitLastCommits <<= (gitIsRepository, gitLastCommitsCount) map { (isRepo, lastCommitsCount) =>
      ifRepo(isRepo) {
        (("git log --oneline --decorate --max-count=%s".format(lastCommitsCount)) !!).split("\n")
      }
    },
    gitBranchName <<= (gitIsRepository) map { isRepo =>
      ifRepo(isRepo) {
        ("git symbolic-ref HEAD" !!).trim
      }
    },
    gitTagName <<= (organization, name, version) map { (o, n, v) =>
      "org=%s,name=%s,version=%s".format(o, n, v)
    },
    gitTag <<= (gitTagName) map { tag =>
      ("git tag -m %s %s".format(tag, tag)).run(false).exitValue
    },
    gitCommitMessage <<= (organization, name, version) map { (o, n, v) =>
      "release commit for %s:%s:%s".format(o, n, v)
    },
    gitCommit <<= (gitCommitMessage) map { m =>
      val pb = ("git add ." #&& Seq("git", "commit", "-m", "'%s'".format(m)))
      val proc = pb.run(false)
      proc.exitValue
    }
  )
}
