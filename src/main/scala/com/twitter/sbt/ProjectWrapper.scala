package com.twitter.sbt

import _root_.sbt._

class WrappedDefaultProject(val underlying: DefaultProject)
	extends StandardProject(underlying.info)
	// extends DefaultProject(underlying.info)
{
	override def name								 = underlying.name
	override def libraryDependencies = underlying.libraryDependencies
	override def subProjects				 = Map() ++ underlying.subProjects

	override def compileOrder = underlying.compileOrder
	override def managedStyle = underlying.managedStyle

	override def fullUnmanagedClasspath(config: Configuration) =
		underlying.fullUnmanagedClasspath(config)

	override def managedClasspath(config: Configuration): PathFinder =
		underlying.managedClasspath(config)

	// XXX - compileoptions?, etc. etc. etc.
}
