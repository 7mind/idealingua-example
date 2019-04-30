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
  "com.septimalmind.api"          %% "services-users-api"            % "0.1.0-SNAPSHOT",
  "com.septimalmind.api"          %% "services-companies-api"        % "0.1.0-SNAPSHOT",
  "com.septimalmind.api"          %% "services-auth-api"             % "0.1.0-SNAPSHOT",
  "com.septimalmind.api"          %% "services-shared-api"           % "0.1.0-SNAPSHOT",
  "com.github.pshirshov.izumi.r2" %% "idealingua-v1-compiler"        % "0.7.0",
  "com.github.pshirshov.izumi.r2" %% "idealingua-runtime-rpc-http4s" % "0.7.0"
) ++ Seq(
  compilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.9"),
  compilerPlugin("com.github.ghik" %% "silencer-plugin"    % "1.3.1"),
  compilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.0-M4")
)
