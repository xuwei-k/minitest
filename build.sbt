/*
 * Copyright (c) 2014-2016 by Alexandru Nedelcu.
 * Some rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{crossProject, CrossType}
import sbt.Keys._
import com.typesafe.sbt.GitVersioning

addCommandAlias("ci-all",  ";+clean ;+compile ;+test ;+package")
addCommandAlias("release", ";+publishSigned ;sonatypeReleaseAll")

val Scala211 = "2.11.12"

ThisBuild / scalaVersion := "2.11.12"
ThisBuild / crossScalaVersions := Seq("2.10.7", Scala211, "2.12.4", "2.13.0-M4")

def scalaPartV = Def setting (CrossVersion partialVersion scalaVersion.value)
lazy val crossVersionSharedSources: Seq[Setting[_]] =
  Seq(Compile, Test).map { sc =>
    (unmanagedSourceDirectories in sc) ++= {
      (unmanagedSourceDirectories in sc).value.map { dir =>
        scalaPartV.value match {
          case Some((major, minor)) =>
            new File(dir.getPath + s"_$major.$minor")
          case None =>
            throw new NoSuchElementException("Scala version")
        }
      }
    }
  }

lazy val scalaLinterOptions =
  Seq(
    // Enables linter options
    "-Xlint:adapted-args", // warn if an argument list is modified to match the receiver
    "-Xlint:nullary-unit", // warn when nullary methods return Unit
    "-Xlint:inaccessible", // warn about inaccessible types in method signatures
    "-Xlint:nullary-override", // warn when non-nullary `def f()' overrides nullary `def f'
    "-Xlint:infer-any", // warn when a type argument is inferred to be `Any`
    "-Xlint:missing-interpolator", // a string literal appears to be missing an interpolator id
    "-Xlint:doc-detached", // a ScalaDoc comment appears to be detached from its element
    "-Xlint:private-shadow", // a private field (or class parameter) shadows a superclass field
    "-Xlint:type-parameter-shadow", // a local type parameter shadows a type already in scope
    "-Xlint:poly-implicit-overload", // parameterized overloaded implicit methods are not visible as view bounds
    "-Xlint:option-implicit", // Option.apply used implicit view
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit
    "-Xlint:by-name-right-associative", // By-name parameter of right associative operator
    "-Xlint:package-object-classes", // Class or object defined in package object
    "-Xlint:unsound-match" // Pattern match may not be typesafe
  )

lazy val sharedSettings = Seq(
  scalacOptions in ThisBuild ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath", file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),

  scalacOptions ++= Seq(
    "-unchecked", "-deprecation", "-feature", "-Xlint",
    "-Ywarn-adapted-args", "-Ywarn-dead-code", "-Ywarn-inaccessible",
    "-Ywarn-nullary-override", "-Ywarn-nullary-unit",
    "-Xlog-free-terms"
  ),

  // Version specific options
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v >= 12 =>
      scalaLinterOptions
    case Some((2, 11)) =>
      scalaLinterOptions ++ Seq("-target:jvm-1.6")
    case _ =>
      Seq("-target:jvm-1.6")
  }),

  resolvers ++= Seq(
    "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
    Resolver.sonatypeRepo("releases")
  ),

  testFrameworks := Seq(new TestFramework("minitest.runner.Framework"))
)

lazy val scalaJSSettings = Seq(
  scalaJSStage in Test := FastOptStage
)

lazy val nativeSettings = Seq(
  nativeLinkStubs := true,
  scalaVersion := Scala211,
  crossScalaVersions := Seq(Scala211)
)

lazy val needsScalaParadise = settingKey[Boolean]("Needs Scala Paradise")

lazy val requiredMacroCompatDeps = Seq(
  needsScalaParadise := {
    val sv = scalaVersion.value
    (sv startsWith "2.10.") || (sv startsWith "2.11.") || (sv startsWith "2.12.") || (sv == "2.13.0-M3")
  },
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % Compile,
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided,
    "org.typelevel" %% "macro-compat" % "1.1.1",
  ),
  libraryDependencies ++= {
    if (needsScalaParadise.value) Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch))
    else Nil
  },
  scalacOptions ++= {
    if (needsScalaParadise.value) Nil
    else Seq("-Ymacro-annotations")
  }
)

lazy val minitestRoot = project.in(file("."))
  .aggregate(minitestJVM, minitestJS, lawsJVM, lawsJS, minitestNative)
  .settings(
    name := "minitest root",
    Compile / sources := Nil,
    skip in publish := true,
  )

lazy val minitest = crossProject(JVMPlatform, JSPlatform, NativePlatform).in(file("."))
  .settings(
    name := "minitest",
    sharedSettings,
    crossVersionSharedSources,
    requiredMacroCompatDeps
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.scala-sbt" % "test-interface" % "1.0"
    ),
  )
  .platformsSettings(JVMPlatform, JSPlatform)(
    unmanagedSourceDirectories in Compile += {
      (baseDirectory in LocalRootProject).value / "jvm_js/src/main/scala"
    }
  )
  .platformsSettings(JVMPlatform, NativePlatform)(
    libraryDependencies ++= Seq(
      "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided"
    )
  )
  .jsSettings(
    scalaJSSettings,
    libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion
  )
  .nativeSettings(
    nativeSettings,
    libraryDependencies += "org.scala-native" %%% "test-interface" % nativeVersion
  )

lazy val minitestJVM    = minitest.jvm
lazy val minitestJS     = minitest.js
lazy val minitestNative = minitest.native

lazy val laws = crossProject(JVMPlatform, JSPlatform, NativePlatform).in(file("laws"))
  .dependsOn(minitest)
  .settings(
    name := "minitest-laws",
    sharedSettings,
    crossVersionSharedSources,
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck" % "1.14.0"
    )
  )
  .jsSettings(
    scalaJSSettings
  )
  .nativeSettings(
    nativeSettings
  )

lazy val lawsJVM    = laws.jvm
lazy val lawsJS     = laws.js
// lazy val lawsNative = laws.native
