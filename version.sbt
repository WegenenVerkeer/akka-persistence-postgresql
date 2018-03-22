val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.10.0" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
