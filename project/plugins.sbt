resolvers += Resolver.typesafeRepo("releases") // Play seems to require quite a few things from here

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.8")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.4.0")
