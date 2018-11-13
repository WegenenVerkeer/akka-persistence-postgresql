import sbt.Keys._
import sbt._

object BuildSettings {

  def commonSettings = Seq(
    parallelExecution in Test := false,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    resolvers ++= Seq(
      "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository",
      Resolver.typesafeRepo("releases")
    ),
    updateOptions := updateOptions.value.withCachedResolution(true),
    organization := "be.wegenenverkeer"
  )

  val publishingCredentials = (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield
    Seq(Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      username,
      password)
    )).getOrElse(Seq())


  val publishSettings = Seq(
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false},
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    pomExtra := <url>https://github.com/WegenenVerkeer/akka-persistence-postgresql</url>
      <licenses>
        <license>
          <name>MIT licencse</name>
          <url>http://opensource.org/licenses/MIT</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <developers>
        <developer>
          <id>AWV</id>
          <name>De ontwikkelaars van AWV</name>
          <url>http://www.wegenenverkeer.be</url>
        </developer>
      </developers>,
    credentials ++= publishingCredentials
  )

}
