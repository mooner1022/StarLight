import java.text.SimpleDateFormat
import java.util.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.about.libs.plugin)
}

val nightly = true
val timeMillis = System.currentTimeMillis() + 2 // Invalidate cache

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "dev.mooner.starlight"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        resourceConfigurations.add("en")
        resourceConfigurations.add("ko")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            val suffix = "-${SimpleDateFormat("yyMMdd").format(timeMillis)}"
            versionNameSuffix = suffix
            buildOutputs.all {
                val variantOutputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                variantOutputImpl.outputFileName = "StarLight-${libs.versions.versionName.get()}$suffix.apk"
            }
        }

        debug {
            val generator = { alphabet: String, n: Int ->
                Random().run {
                    (1..n).joinToString("") { alphabet[nextInt(alphabet.length)].toString() }.toString()
                }
            }

            val nightlySuffix = if (nightly) "_nightly" else ""

            versionNameSuffix = "-build_${generator(('A'..'Z').joinToString("") + ('0'..'9').joinToString(""), 6)}$nightlySuffix"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        //freeCompilerArgs = listOf("-Xcontext-receivers")
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }

    namespace = "dev.mooner.starlight"
}

dependencies {
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.kotlin)
    implementation(libs.coil)
    implementation(libs.lottie)
    implementation(libs.bundles.material.dialogs)
    implementation(libs.recyclerview.animators)
    implementation(libs.imagepicker)
    debugImplementation(libs.leakcanary.android)
    implementation(libs.jsoup)
    implementation(libs.flexbox)
    implementation(libs.chip.navigation.bar)
    implementation(libs.process.phoenix)
    implementation(libs.about.libs)
    implementation(libs.about.libs.core)
    implementation(libs.fast.adapter)
    implementation(libs.fast.adapter.extensions.binding)
    implementation(libs.markwon.core)
    implementation(libs.dalvik.dx)

    implementation(libs.cascade)

    implementation(files("libs/bottomsheets-release.aar"))
    implementation(files("libs/files-release.aar"))

    implementation(files("libs/PeekAlert-release.aar"))

    //implementation(group = "org.mozilla", name = "rhino-runtime", version = "1.7.13") // Remaining on version 1.7.13 due to NoClassDefFoundError
    implementation(files("libs/rhino-1.7.15-SNAPSHOT.jar"))
    implementation(files("libs/rhino-android-release.aar"))

    implementation(projects.configDSL)
    implementation(projects.pluginCore)
}
