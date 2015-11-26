val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.2.1" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
