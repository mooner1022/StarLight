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
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
}
/**
 * KSP2 analyses in the Gradle daemon, and with several of its tasks running at once, one of them can analyse without
 * the Android classes: their types come out as error types, and the agent docs' keys stop matching the code
 * (docs/agent-docs.md §4). KSP tasks therefore run one at a time.
 */
abstract class KspOneAtATime : BuildService<BuildServiceParameters.None>

val kspOneAtATime = gradle.sharedServices.registerIfAbsent("kspOneAtATime", KspOneAtATime::class) {
    maxParallelUsages.set(1)
}

subprojects {
    tasks.withType<com.google.devtools.ksp.gradle.KspAATask>().configureEach {
        usesService(kspOneAtATime)
    }
}
