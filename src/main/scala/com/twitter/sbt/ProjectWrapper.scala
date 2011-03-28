package com.twitter.sbt

import scala.reflect.Manifest

import _root_.sbt._

class WrappedDefaultProject(val underlying: DefaultProject)
  extends StandardProject(underlying.info)
{
  override def name                = underlying.name
	override def version             = underlying.version
  override def organization        = underlying.organization
  override def scratch             = underlying.scratch
  override def libraryDependencies = underlying.libraryDependencies
  override def subProjects         = Map() ++ underlying.subProjects
  override def repositories        = underlying.repositories

  // Ivy stuff.
	override def ivyUpdateConfiguration = underlying.ivyUpdateConfiguration
  override def ivyUpdateLogging       = underlying.ivyUpdateLogging
  override def ivyRepositories        = underlying.ivyRepositories
  override def otherRepositories      = underlying.otherRepositories
  override def ivyValidate            = underlying.ivyValidate
  override def ivyScala               = underlying.ivyScala
  override def ivyCacheDirectory      = underlying.ivyCacheDirectory
  override def ivyPaths               = underlying.ivyPaths
  override def inlineIvyConfiguration = underlying.inlineIvyConfiguration

  override def ivyConfiguration    = underlying.ivyConfiguration
  override def ivySbt              = underlying.ivySbt
  override def ivyModule           = underlying.ivyModule

  override def updateTask(module: => IvySbt#Module, configuration: => UpdateConfiguration) = task {
    underlying.updateTask(module, configuration).run
  }

  override def moduleSettings        = underlying.moduleSettings
  override def inlineSettings        = underlying.inlineSettings
  override def compatTestFramework   = underlying.compatTestFramework
  override def defaultModuleSettings = underlying.defaultModuleSettings
  override def externalSettings      = underlying.externalSettings
  override def outputPattern         = underlying.outputPattern
  override def ivyXML                = underlying.ivyXML
  override def pomExtra              = underlying.pomExtra
  override def ivyConfigurations     = underlying.ivyConfigurations

  override def extraDefaultConfigurations      = underlying.extraDefaultConfigurations
  override def useIntegrationTestConfiguration = underlying.useIntegrationTestConfiguration
  override def defaultConfiguration            = underlying.defaultConfiguration
  override def useMavenConfigurations          = underlying.useMavenConfigurations
  override def useDefaultConfigurations        = underlying.useDefaultConfigurations

  override def mainSourceRoots = underlying.mainSourceRoots

	override def updateModuleSettings  = underlying.updateModuleSettings
	override def updateIvyModule			 = underlying.updateIvyModule
	override def deliverModuleSettings = underlying.deliverModuleSettings
	override def deliverIvyModule			 = underlying.deliverIvyModule
	override def publishModuleSettings = underlying.publishModuleSettings
	override def publishIvyModule			 = underlying.publishIvyModule

	override lazy val clean = task { underlying.clean.run }
  
  // override def cleanAction = underlying.cleanAction
  
  // override protected def updateAction               = underlying.updateAction
	// override protected def cleanLibAction             = underlying.cleanLibAction
	// override protected def cleanCacheAction           = underlying.cleanCacheAction
	// override protected def deliverProjectDependencies = underlying.deliverProjectDependencies

	override def packageToPublishActions = underlying.packageToPublishActions
	override lazy val makePom = task {
    underlying.makePom.run
  }

  override def compileOptions =
    underlying.compileOptions map { opt => CompileOption(opt.asString) }
  override def compileOrder = underlying.compileOrder
  override def managedStyle = underlying.managedStyle

  override def fullUnmanagedClasspath(config: Configuration) =
    underlying.fullUnmanagedClasspath(config)

  override def managedClasspath(config: Configuration): PathFinder =
    underlying.managedClasspath(config)

  // Properties.
  override def property[T](implicit manifest: Manifest[T], format: Format[T]) = {
		lazy val p = underlying.property(manifest, format)
		new Property[T] with Proxy {
			def self = p
			def update(v: T) { self.update(v) }
			def resolve = self.resolve
		}
	}

	override def propertyLocal[T](implicit manifest: Manifest[T], format: Format[T]) = {
		lazy val p = underlying.propertyLocal(manifest, format)
		new Property[T] with Proxy {
			def self = p
			def update(v: T) { self.update(v) }
			def resolve = self.resolve
		}
	}

	override def propertyOptional[T]
			(defaultValue: => T)
			(implicit manifest: Manifest[T], format: Format[T]) = {
		lazy val p = underlying.propertyOptional(defaultValue)(manifest, format)
		new Property[T] with Proxy {
			def self = p
			def update(v: T) { self.update(v) }
			def resolve = self.resolve
		}
	}

	override def system[T](propName: String)(implicit format: Format[T]) = {
		lazy val p = underlying.system(propName)(format)
		new Property[T] with Proxy {
			def self = p
			def update(v: T) { self.update(v) }
			def resolve = self.resolve
		}
	}

	override def systemOptional[T]
			(propName: String, defaultValue: => T)
			(implicit format: Format[T]) = {
		lazy val p = underlying.systemOptional(propName, defaultValue)(format)
		new Property[T] with Proxy {
			def self = p
			def update(v: T) { self.update(v) }
			def resolve = self.resolve
		}
	}

  // TODO: as needed.
}
