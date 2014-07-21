import sbt._
import sbt.Keys._

import play._
import PlayArtifact._
import sbtbuildinfo.Plugin._
import Dependencies._

trait Membership {

  val appVersion = "1.0-SNAPSHOT"

  def buildInfoPlugin = buildInfoSettings ++ Seq(
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      BuildInfoKey.constant("buildNumber", Option(System.getenv("BUILD_NUMBER")) getOrElse "DEV"),
      BuildInfoKey.constant("buildTime", System.currentTimeMillis)
    ),
    buildInfoPackage := "app"
  )

  val commonSettings = Seq(
    organization := "com.gu",
    version := appVersion,
    scalaVersion := "2.10.4",
    resolvers += "Guardian Github Releases" at "http://guardian.github.io/maven/repo-releases",
    parallelExecution in Global := false,
    javaOptions in Test += "-Dconfig.resource=dev.conf"
  ) ++ buildInfoPlugin

  def lib(name: String) = Project(name, file(name)).enablePlugins(PlayScala).settings(commonSettings: _*)

  def app(name: String) = lib(name).settings(playArtifactDistSettings: _*).settings(magentaPackageName := name)
}

object Membership extends Build with Membership {
  val scalaforce = lib("scalaforce").settings(libraryDependencies ++= scalaforceDependencies)

  val frontend = app("frontend").dependsOn(scalaforce)
                .settings(libraryDependencies ++= frontendDependencies)
                .settings(addCommandAlias("devrun", "run -Dconfig.resource=dev.conf 9100"): _*)

  val root = Project("root", base=file(".")).aggregate(frontend)
}