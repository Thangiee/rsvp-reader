ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val kyoVersion    = "1.0-RC1"
lazy val laminarVersion = "17.2.1"

lazy val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))

lazy val sharedJVM = shared.jvm
lazy val sharedJS  = shared.js

lazy val backend = project
  .in(file("backend"))
  .dependsOn(sharedJVM)
  .settings(
    libraryDependencies ++= Seq(
      "io.getkyo" %% "kyo-tapir" % kyoVersion
    )
  )

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % laminarVersion
    )
  )
