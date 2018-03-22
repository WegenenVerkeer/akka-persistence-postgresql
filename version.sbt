val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.11.0-SNAPSHOT"

isSnapshot := version.value.endsWith(snapshotSuffix)
