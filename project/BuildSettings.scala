import sbt.Keys._
import sbt._

trait BuildSettings {
  this: Build =>

  val projectName = "akka-persistence-postgresql"

  def project(moduleName: String): Project = {
    Project(
      id = moduleName,
      base = file("modules/" + moduleName),
      settings = projectSettings() ++ publishSettings
    )
  }

  def mainProject(modules: ProjectReference*): Project = {
    Project(
      id = projectName,
      base = file("."),
      settings = projectSettings() ++ Seq(publishLocal := {}, publish := {})
    ).aggregate(modules: _*)
  }

  private def projectSettings() = {

    val projectSettings = Seq(
      parallelExecution in Test := false,
      parallelExecution in Global := false,
      parallelExecution in ThisBuild := false,
      resolvers ++= Seq(
        "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository",
        Resolver.typesafeRepo("releases"),
        "akka snapshots" at "http://repo.akka.io/snapshots"
      ),
      credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
      updateOptions := updateOptions.value.withCachedResolution(true),
      organization := "be.wegenenverkeer",
      scalaVersion := "2.11.7"
    )

    Defaults.coreDefaultSettings ++ projectSettings
  }

  def publishSettings: Seq[Setting[_]] = Seq(
    publishTo <<= version { (v: String) =>
      val nexus = "https://collab.mow.vlaanderen.be/nexus/content/repositories/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("collab snapshots" at nexus + "snapshots")
      else
        Some("collab releases" at nexus + "releases")
    },
    //    publishMavenStyle := true,
    publishArtifact in Compile := true,
    publishArtifact in Test := true
  )


}