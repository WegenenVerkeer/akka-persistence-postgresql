logLevel := Level.Warn

addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.0")

// supports release in maven central

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.5.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
