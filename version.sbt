val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.9.0" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
