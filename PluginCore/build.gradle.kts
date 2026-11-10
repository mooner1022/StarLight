import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

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
    // The public API, dumped to api/PluginCore.api: checkKotlinAbi fails when the code no longer matches it, and
    // updateKotlinAbi rewrites it. The dump of each release, kept in api/releases, records which version added what.
    // See docs/plugincore-compatibility.md.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        // Android has no main compilation; every variant's classes are dumped together (full's plugin loaders too).
        binariesSource.set(org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource.NON_TEST_COMPILATIONS)
    }
}

val generateApiSince = tasks.register<GenerateApiSince>("generateApiSince") {
    releaseDumps.from(fileTree("api/releases") { include("*.api") })
    currentDump.set(layout.projectDirectory.file("api/PluginCore.api"))
    currentVersion.set(libs.versions.versionName)
    outputDirectory.set(layout.buildDirectory.dir("generated/apiSince"))
    // Reads the dump updateKotlinAbi writes, when both run in one build.
    mustRunAfter("updateKotlinAbi")
}

// Into the classes.jar of every variant's AAR, where slplugin reads it from the PluginCore a plugin is built against.
androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(generateApiSince, GenerateApiSince::outputDirectory)
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

/**
 * Writes `starlight/plugincore-api.json`: the PluginCore version that added each public class and member, for those
 * newer than the oldest release dump (the baseline). slplugin reads it to find the lowest `api_version` a plugin's code
 * needs. The format is shared with slplugin; see docs/plugincore-compatibility.md.
 *
 * A class or member takes the version of the first release dump that has it, or [currentVersion] when no release has
 * it yet. Those of the baseline are left out: nothing is known of what came before it.
 */
@CacheableTask
abstract class GenerateApiSince : DefaultTask() {

    /** `api/releases/<version>.api`, the public API of each release as it shipped. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val releaseDumps: ConfigurableFileCollection

    /** `api/PluginCore.api`, the public API now; `checkKotlinAbi` keeps it matching the code. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val currentDump: RegularFileProperty

    @get:Input
    abstract val currentVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private class Dump(val classes: Set<String>, val members: Set<String>)

    @TaskAction
    fun generate() {
        val releases = releaseDumps.files
            .map { versionOf(it.nameWithoutExtension) to parse(it) }
            .sortedWith { a, b -> compareVersions(a.first, b.first) }
        check(releases.isNotEmpty()) { "No release dump in api/releases; the oldest one is the baseline." }
        val baseline = releases.first().first
        val current = parse(currentDump.get().asFile)

        fun sinceOf(key: String, keys: (Dump) -> Set<String>): String? =
            when (val first = releases.firstOrNull { key in keys(it.second) }?.first) {
                baseline -> null
                null -> currentVersion.get()
                else -> first
            }

        val classes = current.classes.sorted().mapNotNull { key -> sinceOf(key, Dump::classes)?.let { key to it } }
        val members = current.members.sorted().mapNotNull { key -> sinceOf(key, Dump::members)?.let { key to it } }

        fun Iterable<Pair<String, String>>.json() =
            joinToString(",\n", prefix = "{\n", postfix = "\n  }") { (key, since) -> "    \"${escape(key)}\": \"$since\"" }
                .let { if (it == "{\n\n  }") "{}" else it }

        val json = """
            |{
            |  "format": 1,
            |  "version": "${currentVersion.get()}",
            |  "baseline": "$baseline",
            |  "classes": ${classes.json()},
            |  "members": ${members.json()}
            |}
            |""".trimMargin()
        val output = outputDirectory.get().asFile.resolve(OUTPUT_PATH)
        output.parentFile.mkdirs()
        output.writeText(json, Charsets.UTF_8)
    }

    /**
     * Reads a dump in the Kotlin ABI validation format: a class line ends with `{`, its members follow indented by a
     * tab, and `}` closes it. Classes are keyed by their JVM name, methods as `owner#name(descriptor)return` and fields
     * as `owner#name:type`.
     */
    private fun parse(file: File): Dump {
        val classes = mutableSetOf<String>()
        val members = mutableSetOf<String>()
        var owner: String? = null
        file.forEachLine(Charsets.UTF_8) { line ->
            when {
                line.isBlank() || line.startsWith("//") -> {}
                !line.startsWith("\t") && line.trimEnd().endsWith("{") -> {
                    val name = line.substringBefore(" : ").trimEnd().removeSuffix("{").trimEnd().substringAfterLast(' ')
                    classes += name
                    owner = name
                }
                line.trim() == "}" -> owner = null
                owner != null -> {
                    val tokens = line.trim().split(' ')
                    val kind = tokens.indexOfFirst { it == "fun" || it == "field" }
                    if (kind >= 0 && kind + 2 < tokens.size)
                        members += if (tokens[kind] == "fun") "$owner#${tokens[kind + 1]}${tokens[kind + 2]}"
                            else "$owner#${tokens[kind + 1]}:${tokens[kind + 2]}"
                }
            }
        }
        return Dump(classes, members)
    }

    private fun versionOf(name: String): String {
        require(Regex("""\d+\.\d+\.\d+""").matches(name)) { "Release dump $name.api isn't named <major>.<minor>.<patch>.api" }
        return name
    }

    private fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map(String::toInt)
        val y = b.split('.').map(String::toInt)
        return x.zip(y).map { (i, j) -> i.compareTo(j) }.firstOrNull { it != 0 } ?: 0
    }

    private fun escape(text: String) =
        text.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val OUTPUT_PATH = "starlight/plugincore-api.json"
    }
}
