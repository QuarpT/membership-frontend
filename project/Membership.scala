import Dependencies._
import PlayArtifact._
import play.sbt.PlayScala
import sbt.Keys._
import sbt._
import sbtbuildinfo.Plugin._

trait Membership {

  val appVersion = "1.0-SNAPSHOT"

  def buildInfoPlugin = buildInfoSettings ++ Seq(
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      BuildInfoKey.constant("buildNumber", Option(System.getenv("BUILD_NUMBER")) getOrElse "DEV"),
      BuildInfoKey.constant("buildTime", System.currentTimeMillis),
      BuildInfoKey.constant("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse(try {
        "git rev-parse HEAD".!!.trim
      } catch {
          case e: Exception => "unknown"
      }))
    ),
    buildInfoPackage := "app"
  )

  val commonSettings = Seq(
    organization := "com.gu",
    version := appVersion,
    scalaVersion := "2.11.6",
    resolvers ++= Seq(
      "Guardian Github Releases" at "https://guardian.github.io/maven/repo-releases",
      "Guardian Github Snapshots" at "http://guardian.github.com/maven/repo-snapshots",
      "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
      Resolver.sonatypeRepo("releases")),
    sources in (Compile,doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    parallelExecution in Global := false,
    updateOptions := updateOptions.value.withCachedResolution(true),
    javaOptions in Test += "-Dconfig.resource=dev.conf"
  ) ++ buildInfoPlugin

  def lib(name: String) = Project(name, file(name)).enablePlugins(PlayScala).settings(commonSettings: _*)

  def app(name: String) = lib(name).settings(playArtifactDistSettings: _*).settings(magentaPackageName := name)
    .settings(play.sbt.routes.RoutesKeys.routesImport ++= Seq(
      "controllers.TierBinder._",
      "com.gu.membership.salesforce.Tier",
      "com.gu.membership.salesforce.PaidTier"
    ))
}

object Membership extends Build with Membership {
  val frontend = app("frontend")
                .settings(libraryDependencies ++= frontendDependencies)
                .settings(addCommandAlias("devrun", "run -Dconfig.resource=dev.conf 9100"): _*)
                .settings(libraryDependencies ++= acceptanceTestDependencies)
                .settings(addCommandAlias("test", "testOnly -- -l Acceptance"))
                .settings(addCommandAlias("acceptance-test", "testOnly acceptance.JoinPartnerSpec"))

  val root = Project("root", base=file(".")).aggregate(frontend)
}
