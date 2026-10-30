pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        flatDir {
            dirs("libs")
        }
        maven { url = uri("https://jitpack.io") }
        maven("https://mooner1022.github.io/neon-js/maven") {
            content { includeGroup("dev.mooner.neonjs") }
        }
    }
}

include(":app")
include(":PluginCore")
include(":ConfigDSL")

rootProject.name = "StarLight"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")