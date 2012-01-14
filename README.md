# standard-project

Standard-project is a basic set of extensions to sbt to codify best
practices.

# General Usage

## Getting StandardProject

See https://github.com/harrah/xsbt/wiki/Plugins for information on
adding plugins. In general, you'll need to add the following to your
project/plugins.sbt file:

`addSbtPlugin("com.twitter" % "standard-project" % "11.0.0")`

## Mixing in StandardProject

StandardProject is a collection of many different plugins. Each plugin
provides a set of SBT Settings, Tasks and/or Commands that your
project can use. The way an SBT project "uses" these extensions is by
adding them to the project's settings map. There are two ways to
include settings into your build definition

### Using a .sbt file

If you want to include all the StandardProject settings, the following
works

    import com.twitter.sbt._

    seq(StandardProject.newSettings: _*)
    
If you want to include only a specific plugin's settings, you can
specify just the plugin(s) you want

    import com.twitter.sbt._
    
    seq(GitProject.gitSettings: _*)
    
### Using a .scala build definition

In your scala build definition, just extend the settings of any
defined projects, e.g.

    import sbt._
    import Keys._
    import com.twitter.sbt._
    
    object Util extends Build {
        lazy val root = Project(id = "util", base = file("."))
            settings (GitProject.settings: _*)
    }
    
# Reference

## Plugins

Standard project provides the following plugins you can extend

### StandardProject

This aggregates a "reasonable" set of plugins into a single plugin.
Currently included are

* DefaultRepos
* GitProject
* BuildProperties
* PublishLocalWithMavenStyleBasePattern
* PublishSourcesAndJavadocs
* PackageDist
* SubversionPublisher
* VersionManagement
* ReleaseManagement

### DefaultRepos

Sets up a default set of repos used in most Twitter projects. If the
SBT__TWITTER environment variable is set it uses a proxy repo
(configurable via the internal-private-proxy-resolver setting). If the
SBT__OPEN__TWITTER environment variable is set it uses a different
proxy repo (configurable via the internal-public-proxy-resolver setting)

### GitProject

Adds various settings and commands for dealing with git-based
projects, including

* git-is-repository
* git-project-sha
* git-last-commits-count
* git-last-commits
* git-branch-name
* git-commit-message
* git-commit
* git-tag
* git-tag-name

### BuildProperties

Uses GitProject to write a build.properties file containing
information about the environment used to produce a jar. Includes the
name, version, build_name (usually a timestamp), git revision, git
branch, and last few commits.

## PublishLocalWithMavenStyleBasePattern

Overrides the default local resolver with a maven style one. Using ivy
style publishing confuses various other tools.

### PublishSourcesAndJavadocs

Adds a dummy file to the scaladoc classpath to prevent scaladoc
blowing up

### PackageDist

Generates a twitter style dist package containing libs, a manifest
with classpath, copied scripts and configs, etc.

### SubversionPublisher

Adds the ability to publish artifacst to a subversion repo. Settings
of interest are

* subversion-prefs-file - your credentials go in this file
* subversion-user - if you want to hardcode this
* subversion-password - if you're naughty and want to put this in
  source control
* subversion-repository - the url of your svn repo

### VersionManagement

Provides commands to modify the current project version, including

* version-bump-major - tick major rev by 1, reset minor/patch to zero
* version-bump-minor - tick minor rev by 1, reset patch to zero
* version-bump-patch - tick patch by 1
* version-to-snapshot - add -SNAPSHOT to the current version
* version-to-stable - remove -SNAPSHOT from the current version
* version-set - set to an arbitrary version

These commands look in build.sbt files and project/*.scala files for a
current version string and replace it with the updated version. After
this is done the command reloads the project, which should now have
the updated version

### ReleaseManagement

Provides commands for publishing a release. The primary command is
release-publish, which does the following in sequence

* release-ready - make sure the working directory is clean, we don't
  depend on snapshots, and we haven't already tagged this release
* version-to-stable - strip the snapshot version
* publish-local
* publish
* git-commit - check in our changes
* git-tag - add a tag for the current version
* version-bump-patch - bump our version by 1
* version-to-snapshot - we always work off snapshots
* git-commit - check in changes

# Current State

## Done
* DefaultRepos
* BuildProperties
* PublishSourcesAndJavadocs
* StandardProject
* PublishLocalWithMavenStyleBasePattern
* SourceControlledProject
* PackageDist
* SubversionPublisher
* Versions
* PimpedVersion (-> Versions)
* ReleaseManagement

## Unnecessary 
* EnsimeGenerator - superceded by https://github.com/aemoncannon/ensime-sbt-cmd
* AdhocInlines - superceded by profect refs 
* InlineDependencies - superceded by project refs
* IntransitiveCompiles - superceded by project refs
* CachedProjects - superceded by project refs
* ProjectDependencies - superceded by project refs
* LibDirClasspath - should already be supported
* NoisyDependencies - superceded by conflictWarning setting
* CorrectDependencies - defunct?
* StrictDependencies - defunct?
* Tartifactory - defunct
* ManagedClasspathFilter - superceded by project refs?

## TODO (roughly prioritized):
* TESTS
* Standard Projects (library, service)
* Test task overrides (NO_TESTS)
* Overridable ivy cache (SBT_CACHE)
* Default compile options
* Default compile order
* PublishThrift
* PublishSite
* GithubPublisher
* DependencyChecking
* IntegrationSpecs
* Ramdiskable
* TemplateProject
* UnpublishedProject
