import sbt._

trait Dependencies { this: Build =>

  val akkaVersion = "2.3.12"

  val slick                 = "com.typesafe.slick"         %%    "slick"                          % "3.0.0"
  val slickPg               = "com.github.tminglei"        %%    "slick-pg"                       % "0.9.1"
  val akkaPersistence       = "com.typesafe.akka"          %%    "akka-persistence-experimental"  % akkaVersion
  val akkaSlf4j             = "com.typesafe.akka"          %%    "akka-slf4j"                     % akkaVersion
  val akkaActor             = "com.typesafe.akka"          %%    "akka-actor"                     % akkaVersion
  val akkaStreams           = "com.typesafe.akka"          %%    "akka-stream-experimental"       % "1.0"
  val playJson              = "com.typesafe.play"          %%    "play-json"                      % "2.4.2"

  val scalaTest              = "org.scalatest"            %%    "scalatest"               % "2.2.5"     % "test"
  val akkaTest               = "com.typesafe.akka"        %%    "akka-testkit"            % akkaVersion % "test"
  val akkaPersistenceTestkit = "com.github.krasserm"      %% "akka-persistence-testkit"   % "0.3.4"     % "test"

  val hikariCp               = "com.zaxxer"                 % "HikariCP"                  % "2.4.0"

  val mainTestDependencies = Seq (
    scalaTest
  )

}
