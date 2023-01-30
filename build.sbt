ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "polling",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.6",
      "dev.zio" %% "zio-test" % "2.0.6" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.0.6" % Test,
      "dev.zio" %% "zio-test-magnolia" % "2.0.6" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )