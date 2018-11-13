val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.11.0"

isSnapshot := version.value.endsWith(snapshotSuffix)
