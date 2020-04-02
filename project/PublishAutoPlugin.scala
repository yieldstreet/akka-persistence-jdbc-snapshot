import sbt.Keys._
import sbt._

object PublishAutoPlugin extends AutoPlugin {

  override val trigger: PluginTrigger = allRequirements

  override val requires: Plugins = sbtrelease.ReleasePlugin

  object autoImport {
  }

  override val projectSettings = Seq(
    publishMavenStyle := true,
    publishToSetting(),
    credentialsSetting(),
    pomExtraSetting(),
    homepageSetting()
  )

  def pomExtraSetting() = pomExtra :=
    <scm>
      <url>https://github.com/yieldstreet/akka-persistence-jdbc</url>
      <connection>scm:git@github.com:yieldstreet/akka-persistence-jdbc.git</connection>
    </scm>
    <developers>
      <developer>
        <id>dnvriend</id>
        <name>Dennis Vriend</name>
        <url>https://github.com/dnvriend</url>
      </developer>
      <developer>
        <id>yieldstreet</id>
        <name>Yieldstreet Engineering</name>
        <url>https://github.com/yieldstreet</url>
      </developer>
    </developers>

  def homepageSetting() =
    homepage := Some(url(s"https://github.com/yieldstreet/akka-persistence-jdbc"))

  def publishToSetting() = publishTo := {
    val suffix = if (isSnapshot.value) "snapshots" else "releases"
    Some("Yieldstreet" at s"https://nexus.ops.yieldstreet.net/repository/maven-$suffix/")
  }

  def credentialsSetting() =
    credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
}
