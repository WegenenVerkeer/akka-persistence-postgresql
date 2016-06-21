
scalaVersion := "2.11.8"

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
  "-language:reflectiveCalls",
  "-Ybackend:GenBCode",
  "-Ydelambdafy:method"
)
