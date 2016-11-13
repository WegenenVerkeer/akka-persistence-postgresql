import sbt.Keys._
import sbt._

object BuildSettings {

  val projectName = "akka-persistence-postgresql"

  def subProject(moduleName: String): Project = {
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

  def projectSettings() = {

    val projectSettings = Seq(
      parallelExecution in Test := false,
      concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
      resolvers ++= Seq(
        "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository",
        "AWV nexus releases" at "https://collab.mow.vlaanderen.be/nexus/content/repositories/releases",
        "AWV nexus snapshot" at "https://collab.mow.vlaanderen.be/nexus/content/repositories/snapshots",
        Resolver.typesafeRepo("releases")
      ),
      credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
      updateOptions := updateOptions.value.withCachedResolution(true),
      organization := "be.wegenenverkeer",
      scalaVersion := "2.11.8"
    )

    Defaults.coreDefaultSettings ++ projectSettings
  }

  def publishSettings: Seq[Setting[_]] = Seq(
    publishTo := version { (v: String) =>
      val nexus = "https://collab.mow.vlaanderen.be/nexus/content/repositories/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("collab snapshots" at nexus + "snapshots")
      else
        Some("collab releases" at nexus + "releases")
    }.value,
    publishArtifact in Compile := true,
    publishArtifact in Test := true
  )


}