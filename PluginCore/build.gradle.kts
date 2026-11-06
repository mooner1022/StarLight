import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        consumerProguardFiles("consumer-rules.pro")
    }

    // Matches the app's flavors. Only full can load plugin and library dex; build plugins against full.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            isDefault = true
        }
        create("play") {
            dimension = "distribution"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    namespace = "dev.mooner.starlight.plugincore"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    api(libs.bundles.androidx)
    api(libs.bundles.kotlin)
    api(projects.configDSL)

    testImplementation(libs.junit)

    ksp(projects.docsProcessor)
}

// Script docs for StarLight AI, from the KDoc of what scripts can reach; see docs/agent-docs.md.
ksp {
    arg("starlight.docs.module", "plugincore")
    // The packages scripts use whole, and the classes they reach from Projects, Plugins and Languages.
    arg("starlight.docs.packages", listOf(
        "dev.mooner.starlight.plugincore.chat",
        "dev.mooner.starlight.plugincore.logger",
        "dev.mooner.starlight.plugincore.library",
        "dev.mooner.starlight.plugincore.project.Project",
        "dev.mooner.starlight.plugincore.project.ProjectInfo",
        "dev.mooner.starlight.plugincore.plugin.StarlightPlugin",
        "dev.mooner.starlight.plugincore.plugin.PluginInfo",
        "dev.mooner.starlight.plugincore.language.Language",
    ).joinToString(","))
}
