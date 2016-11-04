import BuildSettings._
import Dependencies._

scalaVersion := "2.11.8"

organization := "be.wegenenverkeer"

scalacOptions in ThisBuild ++= Seq(
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  //  "-Ywarn-dead-code",
//  "-Xfatal-warnings",
  "-language:reflectiveCalls",
  "-Ybackend:GenBCode",
  "-Ydelambdafy:method"
)

lazy val akkaPersistencePgModule = {

  val mainDeps = Seq(scalaJava8Compat, slick, slickHikariCp, hikariCp, postgres,
    akkaPersistence, akkaPersistenceQuery, akkaActor, akkaStreams, akkaTest, akkaPersistenceTestkit, slf4jSimple)

  subProject("akka-persistence-pg")
    .configs(config("it") extend Test)
    .settings(Defaults.itSettings: _*)
    .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)

}

lazy val akkaEsModule = {

  val mainDeps = Seq(scalaJava8Compat, akkaPersistence, akkaTest)

  subProject("akka-es")
    .configs(config("it") extend Test)
    .settings(Defaults.itSettings: _*)
    .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)
    .dependsOn(akkaPersistencePgModule % "test->test;compile->compile")

}

lazy val benchmarkModule = {

  val mainDeps = Seq(scalaJava8Compat, gatling, gatlinHighcharts)

  import io.gatling.sbt.GatlingPlugin

  subProject("benchmark")
    .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)
    .dependsOn(akkaPersistencePgModule % "it->test;test->test;compile->compile", akkaEsModule)
    .enablePlugins(GatlingPlugin)
}

val main = mainProject(
  akkaPersistencePgModule,
  akkaEsModule,
  benchmarkModule
)
