logLevel := Level.Warn

// Dependency Resolution
updateOptions := updateOptions.value.withGigahorse(false)

// Publishing
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.7")