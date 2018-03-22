val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.10.0"

isSnapshot := version.value.endsWith(snapshotSuffix)
