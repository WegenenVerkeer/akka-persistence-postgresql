val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.8.0" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
