import BuildSettings._
import Dependencies._

organization := "be.wegenenverkeer"

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

scalacOptions in ThisBuild := {
  val commonOptions = Seq(
    "-target:jvm-1.8",
    "-encoding",
    "UTF-8",
    "-deprecation",         // warning and location for usages of deprecated APIs
    "-feature",             // warning and location for usages of features that should be imported explicitly
    "-unchecked",           // additional warnings where generated code depends on assumptions
    "-Xlint",               // recommended additional warnings
    "-Ywarn-adapted-args",  // Warn if an argument list is modified to match the receiver
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

  val mainDeps = Seq(
    scalaJava8Compat,
    slick,
    slickHikariCp,
    postgres,
    akkaPersistence,
    akkaPersistenceQuery,
    akkaActor,
    akkaStreams,
    akkaTest,
    akkaPersistenceTestkit,
    slf4jSimple
  )

  val It = config("it") extend Test

  Project(
    id = "akka-persistence-pg",
    base = file("modules/akka-persistence-pg")
  ).configs(It)
    .settings(Defaults.coreDefaultSettings ++ commonSettings ++ publishSettings)
    .settings(Defaults.itSettings: _*)
    .settings(crossScalaVersions := (crossScalaVersions in ThisBuild).value)
    .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)

}

lazy val benchmarkModule = {

  val mainDeps = Seq(scalaJava8Compat, gatling % "it", gatlinHighcharts % "it")

  import _root_.io.gatling.sbt.GatlingPlugin

  Project(
    id = "benchmark",
    base = file("modules/benchmark")
  ).dependsOn(akkaPersistencePgModule % "it->test;test->test;compile->compile")
    .enablePlugins(GatlingPlugin)
    .configs(GatlingIt)
    .settings(Defaults.coreDefaultSettings ++ commonSettings ++ Seq(skip in publish := true))
    .settings(crossScalaVersions := (crossScalaVersions in ThisBuild).value.filter(_ startsWith "2.12"))
    .settings(scalaVersion := crossScalaVersions.value.last)

}

val main = Project(
  id = "akka-persistence-postgresql",
  base = file(".")
).settings(
    Defaults.coreDefaultSettings ++ commonSettings ++
      Seq(publishLocal := {}, publish := {}, packagedArtifacts := Map.empty, crossScalaVersions := Seq.empty)
  )
  .aggregate(akkaPersistencePgModule, benchmarkModule)
