import com.typesafe.sbt.SbtStartScript

name := "TidesServer"

version := "1.0"

scalaVersion := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "Spray" at "http://repo.spray.io"

lazy val root = (project in file(".")).enablePlugins(SbtTwirl)

seq(SbtStartScript.startScriptForClassesSettings: _*)

libraryDependencies ++= {
  val sprayV = "1.3.2"
  val akkaV = "2.3.6"
  Seq(
    "io.spray"            %%  "spray-can"            % sprayV,
    "io.spray"            %%  "spray-routing"        % sprayV,
    "io.spray"            %%  "spray-testkit"        % sprayV  % "test",
    "io.spray"            %%  "spray-json"           % "1.3.1",
    "com.typesafe.akka"   %%  "akka-actor"           % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"         % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"          % "2.3.11" % "test",
    "com.wandoulabs.akka" %% "spray-websocket"       % "0.1.3",
    "log4j"               %   "log4j"                % "1.2.14",
    "org.slf4j"           %   "slf4j-api"            % "1.7.6",
    "org.slf4j"           %   "slf4j-simple"         % "1.7.6",
    "mysql"               %   "mysql-connector-java" % "latest.release",
    "joda-time"           %   "joda-time"            % "2.7",
    "org.joda"            %   "joda-convert"         % "1.2")
}

Revolver.settings