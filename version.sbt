val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.2.0.1" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
