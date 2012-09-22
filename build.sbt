name := "scala-migrations"

description := "Database migrations written in Scala."

homepage := Some(url("http://opensource.imageworks.com/?p=scalamigrations"))

startYear := Some(2008)

organization := "com.imageworks.scala-migrations"

organizationName := "Sony Pictures Imageworks"

organizationHomepage := Some(url("http://www.imageworks.com/"))

licenses += "New BSD License" -> url("http://opensource.org/licenses/BSD-3-Clause")

version := "1.0.4-SNAPSHOT"

scalaVersion := "2.8.2"

crossScalaVersions := Seq("2.8.0", "2.8.1", "2.8.2")

// Append -deprecation to the options passed to the Scala compiler.
scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "com.novocode" % "junit-interface" % "0.10-M1" % "test",
  "log4jdbc" % "log4jdbc" % "1.1" from "http://log4jdbc.googlecode.com/files/log4jdbc4-1.1.jar",
  "org.apache.derby" % "derby" % "[10.5.3.0,11.0)" % "test",
  "org.jmock" % "jmock-junit4" % "[2.5.1,3.0)" % "test",
  "org.slf4j" % "slf4j-log4j12" % "[1.5.8,2.0)")

// Run unit tests serially otherwise races can occur between two
// threads checking if the 'schema_migrations' table exists and
// trying to create it.
parallelExecution in Test := false

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
  <developers>
    <developer>
      <id>blair</id>
      <name>Blair Zajac</name>
      <email>blair@orcaware.com</email>
    </developer>
    <developer>
      <id>jrray</id>
      <name>J. Robert Ray</name>
      <email>jrobertray@gmail.com</email>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:git@github.com:imageworks/scala-migrations.git</connection>
    <developerConnection>scm:git:git@github.com:imageworks/scala-migrations.git</developerConnection>
    <url>git@github.com:imageworks/scala-migrations.git</url>
  </scm>

// Do not include log4jdbc as a dependency.
pomPostProcess := { (node: scala.xml.Node) =>
  val rewriteRule =
    new scala.xml.transform.RewriteRule {
      override def transform(n: scala.xml.Node): scala.xml.NodeSeq = {
        val name = n.nameToString(new StringBuilder).toString
        if (   (name == "dependency")
            && ((n \ "groupId").text == "log4jdbc")
            && ((n \ "artifactId").text == "log4jdbc")) {
          scala.xml.NodeSeq.Empty
        }
        else {
          n
        }
      }
    }
  val transformer = new scala.xml.transform.RuleTransformer(rewriteRule)
  transformer.transform(node)(0)
}

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

useGpg := true

useGpgAgent := true
