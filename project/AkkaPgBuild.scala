import sbt.Keys._
import sbt._

object AkkaPgBuild extends Build with BuildSettings with Dependencies {

  lazy val akkaPersistencePgModule = {

    val mainDeps = Seq(slick, slickPg, akkaPersistence, akkaActor, akkaStreams, akkaTest, akkaPersistenceTestkit, playJson)

    project("akka-persistence-pg")
      .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)

  }
    
  lazy val akkaEsModule = {

    val mainDeps = Seq(akkaPersistence, akkaTest)

    project("akka-es")
      .settings(libraryDependencies ++= mainDeps ++ mainTestDependencies)
      .dependsOn(akkaPersistencePgModule)

  }

  lazy val main = mainProject(
    akkaPersistencePgModule,
    akkaEsModule
  )

}
