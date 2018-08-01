logLevel := Level.Warn

addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.2")

// supports release in maven central

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

addSbtPlugin("com.dwijnand" % "sbt-travisci" % "1.1.1")

