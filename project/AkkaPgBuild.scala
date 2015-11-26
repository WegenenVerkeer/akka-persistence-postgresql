import sbt.Keys._
import sbt._

object AkkaPgBuild extends Build with BuildSettings with Dependencies {

  lazy val akkaPersistencePgModule = {

    val mainDeps = Seq(slick, hikariCp, slickPg, akkaPersistence, akkaPersistenceQuery, akkaActor, akkaStreams, akkaTest, akkaPersistenceTestkit,
      playJson, slf4jSimple)

    project("akka-persistence-pg")
      .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)

  }
   
  lazy val akkaEsModule = {

    val mainDeps = Seq(akkaPersistence, akkaTest)

    project("akka-es")
      .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)
      .dependsOn(akkaPersistencePgModule % "test->test;compile->compile")

  }

  lazy val benchmarkModule = {

    val mainDeps = Seq(gatling, gatlinHighcharts)

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
