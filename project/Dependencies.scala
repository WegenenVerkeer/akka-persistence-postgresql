import sbt._

trait Dependencies { this: Build =>

  val akkaVersion = "2.4.0"

  val akkaPersistence         = "com.typesafe.akka"       %%  "akka-persistence"                    % akkaVersion
  val akkaSlf4j               = "com.typesafe.akka"       %%  "akka-slf4j"                          % akkaVersion
  val akkaActor               = "com.typesafe.akka"       %%  "akka-actor"                          % akkaVersion
  val akkaPersistenceQuery    = "com.typesafe.akka"       %%  "akka-persistence-query-experimental" % akkaVersion
  val akkaStreams             = "com.typesafe.akka"       %%  "akka-stream-experimental"            % "1.0"

  val slick                   = "com.typesafe.slick"      %%  "slick"                      % "3.0.3"
  val slickPg                 = "com.github.tminglei"     %%  "slick-pg"                   % "0.9.2"    
  val playJson                = "com.typesafe.play"       %%  "play-json"                  % "2.4.3"
  
  // Test dependencies
  val scalaTest               = "org.scalatest"           %%  "scalatest"                  % "2.2.5"        % "test,it"
  val akkaTest                = "com.typesafe.akka"       %%  "akka-testkit"               % akkaVersion    % "test,it"
  val akkaPersistenceTestkit  = "com.typesafe.akka"       %%  "akka-persistence-tck"       % "2.4.0"        % "test,it"
  val slf4jSimple             = "org.slf4j"               %   "slf4j-simple"               % "1.7.10"       % "test,it"
  val gatlinHighcharts        = "io.gatling.highcharts"   %   "gatling-charts-highcharts"  % "2.1.5"     //% "test"
  val gatling                 = "io.gatling"              %   "gatling-test-framework"     % "2.1.5"     //% "test"

  val hikariCp                = "com.zaxxer"              %   "HikariCP"                   % "2.4.2"

  val mainTestDependencies = Seq (
    scalaTest
  )

}
