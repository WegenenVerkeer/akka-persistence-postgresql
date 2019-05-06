# Release Procedure

Artefacts are published to [Sonatype OSS](https://oss.sonatype.org/), 
which sync's with Maven Central repositories.

The following procedure is executed manually:

~~~
$ sbt +test # tests the code
$ git tag vX.Y.Z -m "release version X.Y.Z"  
$ sbt +publishSigned  #publishes the signed artefacts to Sonatype staging
$ sbt sonatypeRelease
$ git push --tags origin develop
~~~