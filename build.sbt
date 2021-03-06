name := "quizypeasy"
 
version := "1.0" 
      
lazy val `quizypeasy` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
      
scalaVersion := "2.12.2"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )

libraryDependencies += "com.typesafe.play" %% "play-slick" % "3.0.0" // Slick
libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.11" // Connecteur MySQL
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.4" // BCrypt

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )
