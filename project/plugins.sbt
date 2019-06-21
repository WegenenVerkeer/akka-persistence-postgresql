logLevel := Level.Warn

addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.2")

// supports release in maven central
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.5")
addSbtPlugin("com.jsuereth"   % "sbt-pgp"      % "1.1.2")
addSbtPlugin("com.dwijnand"   % "sbt-travisci" % "1.1.3")
addSbtPlugin("com.dwijnand"   % "sbt-dynver"   % "3.3.0")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt" % "2.0.0")
