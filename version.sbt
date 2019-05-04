val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.12.0"

isSnapshot := version.value.endsWith(snapshotSuffix)
