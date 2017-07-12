import BuildSettings._
import Dependencies._

organization := "be.wegenenverkeer"

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

scalacOptions in ThisBuild := {
  val commonOptions = Seq(
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
    "-Ydelambdafy:method"
  )
  if (scalaVersion.value.startsWith("2.11")) commonOptions ++ Seq("-Ybackend:GenBCode")
  else commonOptions
}

lazy val akkaPersistencePgModule = {

  val mainDeps = Seq(scalaJava8Compat, slick, slickHikariCp, hikariCp, postgres,
    akkaPersistence, akkaPersistenceQuery, akkaActor, akkaStreams, akkaTest, akkaPersistenceTestkit, slf4jSimple)

  Project(
    id = "akka-persistence-pg",
    base = file("modules/akka-persistence-pg"),
    settings = Defaults.coreDefaultSettings ++ commonSettings ++ publishSettings
  )
    .configs(config("it") extend Test)
    .settings(Defaults.itSettings: _*)
    .settings(Seq(crossScalaVersions := Seq("2.11.11", "2.12.2"),
      scalaVersion := crossScalaVersions.value.last)
    )
    .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)

}

lazy val benchmarkModule = {

  val mainDeps = Seq(scalaJava8Compat, gatling % "it", gatlinHighcharts % "it")

  import io.gatling.sbt.GatlingPlugin

  Project(
    id = "benchmark",
    base = file("modules/benchmark"),
    settings = Defaults.coreDefaultSettings ++ commonSettings ++ Seq(publishLocal := {}, publish := {}, packagedArtifacts := Map.empty)
  )
    .dependsOn(akkaPersistencePgModule % "it->test;test->test;compile->compile")
    .enablePlugins(GatlingPlugin)
    .settings(Seq(scalaVersion := "2.12.2"))


}

val main = Project(
  id = "akka-persistence-postgresql",
  base = file("."),
  settings = Defaults.coreDefaultSettings ++ commonSettings ++
    Seq(publishLocal := {}, publish := {}, packagedArtifacts := Map.empty, crossScalaVersions := Seq("2.11.11", "2.12.2"))
).aggregate(akkaPersistencePgModule, benchmarkModule)
