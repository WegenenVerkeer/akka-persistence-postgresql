import sbt._

trait Dependencies { this: Build =>

  val akkaVersion    = "2.4.1"
  val slickVersion   = "3.1.1"
  val slickPgVersion = "0.11.0"

  val akkaPersistence         = "com.typesafe.akka"       %%  "akka-persistence"                    % akkaVersion
  val akkaSlf4j               = "com.typesafe.akka"       %%  "akka-slf4j"                          % akkaVersion
  val akkaActor               = "com.typesafe.akka"       %%  "akka-actor"                          % akkaVersion
  val akkaPersistenceQuery    = "com.typesafe.akka"       %%  "akka-persistence-query-experimental" % akkaVersion
  val akkaStreams             = "com.typesafe.akka"       %%  "akka-stream-experimental"            % "2.0.1"

  val slick                   = "com.typesafe.slick"      %%  "slick"                      % slickVersion
  val slickHikariCp           = "com.typesafe.slick"      %%  "slick-hikaricp"             % slickVersion

  val slickPg                 = "com.github.tminglei"     %%  "slick-pg"                   % slickPgVersion
  val slickPgPlayJson         = "com.github.tminglei"     %%  "slick-pg_play-json"         % slickPgVersion
  val slickPgDate2            = "com.github.tminglei"     %%  "slick-pg_date2"             % slickPgVersion

  val playJson                = "com.typesafe.play"       %%  "play-json"                  % "2.4.6"
  
  // Test dependencies
  val scalaTest               = "org.scalatest"           %%  "scalatest"                  % "2.2.5"        % "test,it"
  val akkaTest                = "com.typesafe.akka"       %%  "akka-testkit"               % akkaVersion    % "test,it"
  val akkaPersistenceTestkit  = "com.typesafe.akka"       %%  "akka-persistence-tck"       % akkaVersion    % "test,it"
  val slf4jSimple             = "org.slf4j"               %   "slf4j-simple"               % "1.7.13"       % "test,it"
  val gatlinHighcharts        = "io.gatling.highcharts"   %   "gatling-charts-highcharts"  % "2.1.7"     //% "test"
  val gatling                 = "io.gatling"              %   "gatling-test-framework"     % "2.1.7"     //% "test"

  val hikariCp                = "com.zaxxer"              %   "HikariCP"                   % "2.4.3"

  val mainTestDependencies = Seq (
    scalaTest
  )

}
