import sbt._

name := "server"

organization in ThisBuild := "com.septimalmind"

version := "1.0.0"

scalaVersion := "2.12.8"

scalacOptions += "-Ypartial-unification"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0")

libraryDependencies := Seq(
  // IDL API
  "com.septimalmind.api"          %% "services-users-api"               % "0.1.0-SNAPSHOT",
  "com.septimalmind.api"          %% "services-companies-api"           % "0.1.0-SNAPSHOT",
  "com.septimalmind.api"          %% "services-auth-api"                % "0.1.0-SNAPSHOT",
  "com.septimalmind.api"          %% "services-shared-api"              % "0.1.0-SNAPSHOT",
  "io.7mind.izumi"                %% "idealingua-v1-compiler"           % "0.8.6",
  "io.7mind.izumi"                %% "idealingua-v1-compiler"           % "0.8.6",
  "io.7mind.izumi"                %% "idealingua-v1-runtime-rpc-http4s" % "0.8.6",
  "dev.zio"                       %% "zio"                              % "1.0.0-RC8-12",
  "dev.zio"                       %% "zio-interop-cats"                 % "1.0.0-RC8-12",
  "org.scalactic"                 %% "scalactic"                        % "3.0.5",
  "org.scalatest"                 %% "scalatest"                        % "3.0.5" % "test"
) ++ Seq(
  compilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.9"),
  compilerPlugin("com.github.ghik" %% "silencer-plugin"    % "1.3.1"),
  compilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.0-M4")
)
