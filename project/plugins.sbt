logLevel := Level.Warn

addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.2")

// supports release in maven central
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.4")
addSbtPlugin("com.jsuereth"   % "sbt-pgp"      % "2.0.1")
addSbtPlugin("com.dwijnand"   % "sbt-travisci" % "1.1.3")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt" % "2.0.1")
