val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.5.0" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
