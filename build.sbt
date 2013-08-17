// Include the Android plugin
androidDefaults

// Name of your app
name := "SSH Beam"

// Version of your app
version := "0.3.5"

// Version number of your app
versionCode := 12

// Version of Scala
scalaVersion := "2.10.2"

// Version of the Android platform SDK
platformName := "android-14"

// Add JSch (SSH library)
libraryDependencies += "com.jcraft" % "jsch" % "0.1.50"

// Scaloid
libraryDependencies += "org.scaloid" %% "scaloid" % "2.2-8"

// Support library
libraryDependencies += "com.android.support" % "support-v4" % "13.0.0"

// Preload Scaloid
preloadFilters += filterName("scaloid")

// Proguard options
proguardOptions += "-keep class com.jcraft.jsch.** { *; }"

// Release key alias
keyalias := "fx_applications"
