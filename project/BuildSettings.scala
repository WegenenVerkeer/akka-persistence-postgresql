import sbt.Keys._
import sbt._

trait BuildSettings { this: Build =>

  val projectName = "akka-persistence-postgresql"

  def project(moduleName: String): Project = {
    Project(
      id = moduleName,
      base = file("modules/" + moduleName),
      settings = projectSettings()
    )
  }

  def mainProject(modules: ProjectReference*): Project = {
    Project(
      id = projectName,
      base = file("."),
      settings = projectSettings()
    ).settings(publishArtifact := false)
      .aggregate(modules: _*)
  }

  private def projectSettings() = {

    val projectSettings = Seq(
      parallelExecution := false,
      resolvers ++= Seq(
        "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository",
        Resolver.typesafeRepo("releases"),
        "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"
        ),
      credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
      updateOptions := updateOptions.value.withCachedResolution(true),
      organization := "be.wegenenverkeer",
      version      := "0.0.1-SNAPSHOT",
      scalaVersion := "2.11.6"
    )

    Defaults.coreDefaultSettings ++ projectSettings ++ publishSettings
  }

  def publishSettings: Seq[Setting[_]] = Seq(
    publishTo <<= version { (v: String) =>
      val nexus = "https://collab.mow.vlaanderen.be/nexus/content/repositories/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("collab snapshots" at nexus + "snapshots")
      else
        Some("collab releases"  at nexus + "releases")
    },
    publishMavenStyle := true
  )


}