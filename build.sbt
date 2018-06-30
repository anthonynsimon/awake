lazy val versions = new {
  val akkaHttp = "10.1.1"
  val akkaHttpCirce = "1.20.1"
  val akkaHttpJackson = "1.20.1"
  val akka = "2.5.12"
  val circe = "0.9.3"
}

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.5"
    )),
    name := "awake",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % versions.akka,

      "io.circe" %% "circe-core" % versions.circe,
      "io.circe" %% "circe-generic" % versions.circe,
      "io.circe" %% "circe-parser" % versions.circe,
      "io.circe" %% "circe-java8" % versions.circe,
      "de.heikoseeberger" %% "akka-http-circe" % versions.akkaHttpCirce,

      "com.typesafe.akka" %% "akka-http-testkit" % versions.akkaHttp % Test,
      "com.typesafe.akka" %% "akka-testkit" % versions.akka % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % versions.akka % Test,
      "org.scalatest" %% "scalatest" % "3.0.1" % Test
    )
  )
