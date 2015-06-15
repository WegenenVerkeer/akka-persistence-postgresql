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
      updateOptions := updateOptions.value.withCachedResolution(true)
    )

    Defaults.coreDefaultSettings ++ projectSettings
  }

}