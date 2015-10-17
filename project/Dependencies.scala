import sbt._

trait Dependencies { this: Build =>

  val akkaVersion = "2.3.14"
  val slickPgVersion = "0.10.0"

  val slick                 = "com.typesafe.slick"         %%    "slick"                          % "3.1.0"
  val slickPg               = "com.github.tminglei"        %%    "slick-pg"                       % slickPgVersion
  val slickPgPlayJson       = "com.github.tminglei"        %%    "slick-pg_play-json"             % slickPgVersion
  val slickPgDate2          = "com.github.tminglei"        %%    "slick-pg_date2"                 % slickPgVersion


  val akkaPersistence       = "com.typesafe.akka"          %%    "akka-persistence-experimental"  % akkaVersion
  val akkaSlf4j             = "com.typesafe.akka"          %%    "akka-slf4j"                     % akkaVersion
  val akkaActor             = "com.typesafe.akka"          %%    "akka-actor"                     % akkaVersion

  val akkaStreams           = "com.typesafe.akka"          %%    "akka-stream-experimental"       % "1.0"
  val playJson              = "com.typesafe.play"          %%    "play-json"                      % "2.4.3"

  val scalaTest              = "org.scalatest"            %%    "scalatest"               % "2.2.5"     % "test"
  val akkaTest               = "com.typesafe.akka"        %%    "akka-testkit"            % akkaVersion % "test"
  val akkaPersistenceTestkit = "com.github.krasserm"      %% "akka-persistence-testkit"   % "0.3.4"     % "test"
  val logback                =  "ch.qos.logback"           % "logback-classic"            % "1.1.3"     % "test"

  val gatlinHighcharts       = "io.gatling.highcharts"     % "gatling-charts-highcharts"  % "2.1.7"
  val gatling                = "io.gatling"                % "gatling-test-framework"     % "2.1.7"

  val hikariCp               = "com.zaxxer"                 % "HikariCP"                  % "2.4.1"

  val mainTestDependencies = Seq (
    scalaTest
  )

}
