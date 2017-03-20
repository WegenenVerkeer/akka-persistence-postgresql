import sbt._

object Dependencies {

  val akkaVersion    = "2.4.12"
  val slickVersion   = "3.1.1"
  val gatlinVersion  = "2.2.1"

  val scalaJava8Compat      = "org.scala-lang.modules"     %%    "scala-java8-compat"             % "0.8.0"

  val akkaPersistence         = "com.typesafe.akka"       %%  "akka-persistence"                    % akkaVersion
  val akkaSlf4j               = "com.typesafe.akka"       %%  "akka-slf4j"                          % akkaVersion
  val akkaActor               = "com.typesafe.akka"       %%  "akka-actor"                          % akkaVersion
  val akkaPersistenceQuery    = "com.typesafe.akka"       %%  "akka-persistence-query-experimental" % akkaVersion
  val akkaStreams             = "com.typesafe.akka"       %%  "akka-stream"                         % akkaVersion

  val slick                   = "com.typesafe.slick"      %%  "slick"                      % slickVersion
  val slickHikariCp           = "com.typesafe.slick"      %%  "slick-hikaricp"             % slickVersion    exclude("com.zaxxer", "HikariCP-java6")

  // Test dependencies
  val scalaTest               = "org.scalatest"           %%  "scalatest"                  % "3.0.0"        % "test,it"
  val akkaTest                = "com.typesafe.akka"       %%  "akka-testkit"               % akkaVersion    % "test,it"
  val akkaPersistenceTestkit  = "com.typesafe.akka"       %%  "akka-persistence-tck"       % akkaVersion    % "test,it"
  val slf4jSimple             = "org.slf4j"               %   "slf4j-simple"               % "1.7.21"       % "test,it"
  val gatlinHighcharts        = "io.gatling.highcharts"   %   "gatling-charts-highcharts"  % "2.2.2"     //% "test"
  val gatling                 = "io.gatling"              %   "gatling-test-framework"     % "2.2.2"     //% "test"

  val hikariCp                = "com.zaxxer"              %   "HikariCP"                   % "2.5.1"
  val postgres                = "org.postgresql"          %   "postgresql"                 % "9.4.1212"

  val mainTestDependencies = Seq (
    scalaTest, akkaSlf4j
  )

}
