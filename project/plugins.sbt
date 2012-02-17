resolvers += Resolver.url("Typesafe repository", new java.net.URL("http://typesafe.artifactoryonline.com/typesafe/ivy-releases/"))(Resolver.defaultIvyPatterns)

resolvers += "twttr" at "http://maven.twttr.com"

libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-tools.sbt" %% "scripted-plugin" % sv
}

libraryDependencies += "ivysvn" % "ivysvn" % "2.1.0"

libraryDependencies += Defaults.sbtPluginExtra("com.twitter" % "standard-project2" % "0.0.3", "0.11.2", "2.9.1")

