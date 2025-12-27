import scala.sys.process._
val scala3Version = "3.7.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scalatensordsl",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,

    run / fork := true, // Must fork a new JVM for flags to take effect
    run / javaOptions ++= Seq(
      "--enable-native-access=ALL-UNNAMED", 
      "-Djava.library.path=lib",
      "--enable-preview" // For FFI
    ),

    // If you are using JDK 21 (LTS), FFM is a "Preview" feature, so you also need:
    // run / javaOptions += "--enable-preview",

    nativeCompile := {
      val log = streams.value.log
      val baseDir = baseDirectory.value
      
      log.info("Checking for changes in native drivers...")

      val exitCode = Process("make", baseDir) ! log
      
      if (exitCode != 0) {
        throw new MessageOnlyException("Native build (Make) failed! Fix C++ errors before compiling Scala.")
      }
    },

    Compile / compile := ((Compile / compile) dependsOn nativeCompile).value,

    clean := {
      val log = streams.value.log
      val baseDir = baseDirectory.value
      Process("make clean", baseDir) ! log
      clean.value
    }
  )

lazy val nativeCompile = taskKey[Unit]("Compiles C++/CUDA drivers using Make")