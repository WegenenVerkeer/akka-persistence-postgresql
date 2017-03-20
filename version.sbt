val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.6.0" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
