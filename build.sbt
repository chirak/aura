name := "aura"

version := "1.0-SNAPSHOT"

resolvers ++= Seq(
  "Typesafe Releases" at "http://typesafe.artifactoryonline.com/typesafe",
  "pk11 repo" at "http://pk11-scratch.googlecode.com/svn/trunk"
)

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache
)     

play.Project.playScalaSettings
