

import com.typesafe.sbt.SbtGit.GitKeys._

git.useGitDescribe := true
git.baseVersion := "0.1.0"


git.gitDescribedVersion := gitReader.value.withGit(_.describedVersion).flatMap(v =>
  Option(v).orElse(git.formattedShaVersion.value).orElse(Some(git.baseVersion.value))
)

val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r
git.gitTagToVersionNumber := {
  case VersionRegex(v,"") => Some(v)
  case VersionRegex(v,"SNAPSHOT") => Some(s"$v-SNAPSHOT")
  case VersionRegex(v,s) => Some(s"$v-$s-SNAPSHOT")
  case _ => None
}
scalaVersion := "2.11.7"

organization := "be.wegenenverkeer"

scalacOptions in ThisBuild ++= Seq(
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  //  "-Ywarn-dead-code",
//  "-Xfatal-warnings",
  "-language:reflectiveCalls"
)