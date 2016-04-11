import sbt.Keys._
import sbt._

object AkkaPgBuild extends Build with BuildSettings with Dependencies {

  lazy val akkaPersistencePgModule = {

    val mainDeps = Seq(scalaJava8Compat, slick, slickHikariCp, hikariCp, slickPg, slickPgPlayJson, slickPgDate2,
      akkaPersistence, akkaPersistenceQuery, akkaActor, akkaStreams, akkaTest, akkaPersistenceTestkit,
      playJson, slf4jSimple)

    project("akka-persistence-pg")
      .configs(config("it") extend(Test))
      .settings(Defaults.itSettings: _*)
      .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)

  }
   
  lazy val akkaEsModule = {

    val mainDeps = Seq(scalaJava8Compat, akkaPersistence, akkaTest)

    project("akka-es")
      .configs(config("it") extend(Test))
      .settings(Defaults.itSettings: _*)
      .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)
      .dependsOn(akkaPersistencePgModule % "test->test;compile->compile")

  }

  lazy val benchmarkModule = {

    val mainDeps = Seq(scalaJava8Compat, gatling, gatlinHighcharts)

    import io.gatling.sbt.GatlingPlugin

    project("benchmark")
      .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)
      .dependsOn(akkaPersistencePgModule % "it->test;test->test;compile->compile", akkaEsModule)
      .enablePlugins(GatlingPlugin)
  }

  lazy val main = mainProject(
    akkaPersistencePgModule,
    akkaEsModule,
    benchmarkModule
  )

}
