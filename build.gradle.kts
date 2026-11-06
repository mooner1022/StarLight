buildscript {
    repositories {
        google()
        mavenCentral()

        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.about.libs.plugin) apply false
}