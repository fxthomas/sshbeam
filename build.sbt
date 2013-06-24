// Include the Android plugin
androidDefaults

// Name of your app
name := "SSH Beam"

// Version of your app
version := "0.2.3"

// Version number of your app
versionCode := 6

// Version of Scala
scalaVersion := "2.10.1"

// Version of the Android platform SDK
platformName := "android-16"

// Add JSch (SSH library)
libraryDependencies += "com.jcraft" % "jsch" % "0.1.50"

// Scaloid
libraryDependencies += "org.scaloid" % "scaloid" % "1.1_8_2.10"

// Proguard options
proguardOptions += "-keep class com.jcraft.jsch.** { *; }"
