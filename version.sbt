val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.7.0" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
