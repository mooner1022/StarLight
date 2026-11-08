import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The StarLight Gradle plugin (slplugin) bundles a copy of this processor for plugins, in plugin/src/docsProcessor
// (docs/agent-docs.md §9). Change it here first, then copy it there.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
