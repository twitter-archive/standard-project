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
