pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central, tried first to avoid Central rate limits.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "elm-iptv"
include(":core", ":ffmpeg", ":app", ":desktop")
