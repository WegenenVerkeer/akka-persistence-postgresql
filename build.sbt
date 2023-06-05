import BuildSettings._
import Dependencies._

organization := "be.wegenenverkeer"

Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

lazy val scala212               = "2.12.17"
lazy val scala213               = "2.13.10"
ThisBuild / crossScalaVersions := Seq(scala212, scala213)

ThisBuild / scalacOptions := {
  val commonOptions = Seq(
    "-release:11",
    "-encoding",
    "UTF-8",
    "-deprecation",         // warning and location for usages of deprecated APIs
    "-feature",             // warning and location for usages of features that should be imported explicitly
    "-unchecked",           // additional warnings where generated code depends on assumptions
    "-Xlint:-infer-any",    // recommended additional warnings
    "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
    //  "-Ywarn-dead-code",
    //  "-Xfatal-warnings",
    "-language:reflectiveCalls",
    "-Ydelambdafy:method",
    //"-Wconf:cat=lint-multiarg-infix:s" // mute warning on Slick <> operator: https://contributors.scala-lang.org/t/multiarg-infix-application-considered-warty/4490
  )

  val scalaVersionSpecificOptions = scalaVersion.value match {
    case v: String if v startsWith "2.13" => Seq()
    case v: String if v startsWith "2.12" =>
      Seq(
        "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver,
        "-Ywarn-inaccessible"
      )
  }

  commonOptions ++ scalaVersionSpecificOptions
}

lazy val akkaPersistencePgModule = {

  val mainDeps = Seq(
    slick,
    slickHikariCp,
    postgres,
    akkaPersistence,
    akkaPersistenceQuery,
    akkaActor,
    akkaStreams,
    akkaTest,
    akkaPersistenceTestkit,
    slf4jSimple,
    scalaCollectionCompat
  )

  val It = config("it") extend Test

  Project(
    id = "akka-persistence-pg",
    base = file("modules/akka-persistence-pg")
  ).configs(It)
    .settings(Defaults.coreDefaultSettings ++ commonSettings ++ publishSettings)
    .settings(Defaults.itSettings: _*)
    .settings(crossScalaVersions := (ThisBuild / crossScalaVersions).value)
    .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)
    .settings(libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2,n)) if n <= 12 => Seq("org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0")
        case _ => Seq("org.scala-lang.modules" %% "scala-java8-compat" % "1.0.0")
      }
    })

}

lazy val benchmarkModule = {

  //val mainDeps = Seq(scalaJava8Compat, gatling % "it", gatlinHighcharts % "it")

  import _root_.io.gatling.sbt.GatlingPlugin

  Project(
    id = "benchmark",
    base = file("modules/benchmark")
  ).dependsOn(akkaPersistencePgModule % "it->test;test->test;compile->compile")
    .enablePlugins(GatlingPlugin)
    .configs(GatlingIt)
    .settings(Defaults.coreDefaultSettings ++ commonSettings ++ Seq(publish / skip := true))
    .settings(crossScalaVersions := (ThisBuild / crossScalaVersions).value.filter(_ startsWith "2.13"))
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
