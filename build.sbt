import AssemblyKeys._
import scala.util.Properties._

name := "Machinery"

val nextMinorVersion = (Process(Seq("cat", "minorVersion.txt")).!!.replaceAll("\n", "").toInt + 1).toString
version := {
  "0." + nextMinorVersion
}

scalaVersion := "2.11.7"

assemblySettings

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "Spray" at "http://repo.spray.io"

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com._338oaklandcreations.fabric.machinery",
    buildInfoOptions += BuildInfoOption.BuildTime
)

libraryDependencies ++= {
  val sprayV = "1.3.2"
  val akkaV = "2.3.6"
  Seq(
    "io.spray"            %%  "spray-can"            % sprayV,
    "io.spray"            %%  "spray-routing"        % sprayV,
    "io.spray"            %%  "spray-testkit"        % sprayV  % "test",
    "io.spray"            %%  "spray-json"           % sprayV,
    "com.typesafe.akka"   %%  "akka-actor"           % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"         % akkaV % "test",
    "com.typesafe.akka"   %%  "akka-slf4j"           % akkaV,
    "org.slf4j"           %   "slf4j-api"            % "1.7.10",
    "ch.qos.logback"      %   "logback-classic"      % "1.1.3",
    "org.specs2"          %%  "specs2-core"          % "2.3.11" % "test",
    "joda-time"           %   "joda-time"            % "2.7",
    "org.joda"            %   "joda-convert"         % "1.2")
}

Revolver.settings

val deployTask = TaskKey[Unit]("deploy", "Copies assembly jar to remote location")

deployTask <<= assembly map { (asm) =>
  val targetDir = envOrElse("FABRIC_JAR_TARGET_DIR", "")
  val account = envOrElse("FABRIC_EMBEDDED_SERVER", "")
  val jarName = asm.getName
  val src = "./target/scala-2.11/" + jarName
  val target = targetDir + jarName
  val target_abs = account + ":" + target
  Process(Seq("scp", src, target_abs)).!
  Process(Seq("bash", "-c", "echo " + nextMinorVersion + " > minorVersion.txt")).!

  // Optional. Create symbolic link to last version
  val symlink = targetDir + "Machinery-assembly.jar"
  println("Symlinking it to  " + symlink)
  val cmd = "ln -sf " + target + " " + symlink
  Process(Seq("ssh", account, cmd)).!
}

