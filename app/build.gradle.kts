import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.DataInputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import java.text.SimpleDateFormat
import java.util.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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

    flavorDimensions += "distribution"
    productFlavors {
        // GitHub builds: dex plugins and the plugin store, /sdcard/StarLight, self-updates.
        create("full") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "DYNAMIC_PLUGINS", "true")
            buildConfigField("boolean", "SELF_UPDATE", "true")
            buildConfigField("boolean", "SHARED_STORAGE", "true")
            buildConfigField("boolean", "RESTRICTED_SCRIPT_HOST_ACCESS", "false")
            buildConfigField("boolean", "SCRIPT_JIT", "true")
        }
        // Google Play builds, kept within the Device and Network Abuse policy: no code is downloaded
        // or loaded from outside the app, no self-updates, no all-files access, and scripts can't reach
        // the Java APIs that would let them do so. Checked by verifyPlay<BuildType>Policy.
        create("play") {
            dimension = "distribution"
            // Required by Google Play for new apps and updates since August 31, 2026.
            targetSdk = 36
            buildConfigField("boolean", "DYNAMIC_PLUGINS", "false")
            buildConfigField("boolean", "SELF_UPDATE", "false")
            buildConfigField("boolean", "SHARED_STORAGE", "false")
            buildConfigField("boolean", "RESTRICTED_SCRIPT_HOST_ACCESS", "true")
            // Script engines compile scripts to dex on the device. Play allows interpreters; set this to
            // false to keep both engines interpreting if a review objects to the compiled tiers.
            buildConfigField("boolean", "SCRIPT_JIT", "true")
        }
    }

    signingConfigs {
        val keystore = System.getenv("KEYSTORE_FILE")?.let(::file)
        if (keystore != null && keystore.isFile) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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

    namespace = "dev.mooner.starlight"
}

// Fails play builds that could load code from outside the app or update themselves, see VerifyPlayPolicy.
androidComponents {
    onVariants(selector().withFlavor("distribution" to "play")) { variant ->
        val variantName = variant.name.replaceFirstChar(Char::uppercaseChar)
        val verify = tasks.register<VerifyPlayPolicy>("verify${variantName}Policy") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/play-policy/${variant.name}.txt"))
        }
        variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
            .use(verify)
            .toGet(ScopedArtifact.CLASSES, VerifyPlayPolicy::allJars, VerifyPlayPolicy::allDirectories)
        tasks.named { it == "assemble$variantName" || it == "bundle$variantName" }
            .configureEach { dependsOn(verify) }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
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
    testImplementation(libs.junit)
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

    implementation(libs.neonjs.core)
    implementation(libs.neonjs.android)
    implementation(libs.neonjs.net)

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

/**
 * Checks what Google Play's Device and Network Abuse policy rules out for the play flavor:
 * - the merged manifest asks for no all-files access and no package installs;
 * - StarLight's own classes don't load dex or native code, start processes, or install APKs.
 *
 * Script engines are left out: they compile scripts on the device, which Play treats as interpreted
 * code. What scripts can reach is limited at run time by ScriptHostPolicy.
 */
abstract class VerifyPlayPolicy : DefaultTask() {

    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val problems = checkManifest() + checkClasses()
        val text = if (problems.isEmpty()) "OK" else problems.joinToString("\n")
        report.get().asFile.apply { parentFile.mkdirs(); writeText(text + "\n") }
        if (problems.isNotEmpty())
            throw GradleException("Play policy check failed:\n" + problems.joinToString("\n") { "  - $it" })
    }

    private fun checkManifest(): List<String> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(mergedManifest.get().asFile)
        val android = "http://schemas.android.com/apk/res/android"
        val problems = mutableListOf<String>()
        val permissions = doc.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val name = (permissions.item(i) as org.w3c.dom.Element).getAttributeNS(android, "name")
            if (name in FORBIDDEN_PERMISSIONS)
                problems += "manifest asks for $name"
        }
        val application = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element?
        if (application?.getAttributeNS(android, "requestLegacyExternalStorage") == "true")
            problems += "manifest sets requestLegacyExternalStorage"
        return problems
    }

    private fun checkClasses(): List<String> {
        val problems = sortedSetOf<String>()
        for (dir in allDirectories.get()) {
            dir.asFile.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .forEach { problems += findForbidden(it.readBytes()) }
        }
        for (jar in allJars.get()) {
            ZipFile(jar.asFile).use { zip ->
                for (entry in zip.entries())
                    if (entry.name.endsWith(".class"))
                        problems += findForbidden(zip.getInputStream(entry).use { it.readBytes() })
            }
        }
        return problems.toList()
    }

    /** Reads the class file's constant pool for references to forbidden classes, methods and strings. */
    private fun findForbidden(bytes: ByteArray): List<String> {
        val input = DataInputStream(bytes.inputStream())
        if (input.readInt() != 0xCAFEBABE.toInt())
            return emptyList()
        input.skipBytes(4)
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val classIndex = IntArray(count)
        val stringIndex = IntArray(count)
        val nameAndType = IntArray(count)
        val memberRefs = mutableListOf<Pair<Int, Int>>()
        var i = 1
        while (i < count) {
            when (input.readUnsignedByte()) {
                1 -> utf8[i] = input.readUTF()
                3, 4 -> input.skipBytes(4)
                5, 6 -> { input.skipBytes(8); i++ }
                7 -> classIndex[i] = input.readUnsignedShort()
                8 -> stringIndex[i] = input.readUnsignedShort()
                9, 10, 11 -> memberRefs += input.readUnsignedShort() to input.readUnsignedShort()
                12 -> { nameAndType[i] = input.readUnsignedShort(); input.skipBytes(2) }
                15 -> input.skipBytes(3)
                16, 19, 20 -> input.skipBytes(2)
                17, 18 -> input.skipBytes(4)
                else -> return emptyList()
            }
            i++
        }
        input.skipBytes(2)
        val self = utf8[classIndex[input.readUnsignedShort()]] ?: return emptyList()
        if (OWN_PACKAGES.none(self::startsWith))
            return emptyList()

        fun className(index: Int): String = utf8[classIndex[index]].orEmpty()
        val found = mutableListOf<String>()
        for (index in 1 until count) {
            if (classIndex[index] != 0) {
                val name = className(index)
                if (FORBIDDEN_CLASSES.any { name == it || name.startsWith("$it$") })
                    found += "$self uses $name"
            }
            val string = if (stringIndex[index] != 0) utf8[stringIndex[index]] else null
            if (string in FORBIDDEN_STRINGS)
                found += "$self uses \"$string\""
        }
        for ((owner, nat) in memberRefs) {
            val member = className(owner) + "." + utf8[nameAndType[nat]]
            if (member in FORBIDDEN_MEMBERS)
                found += "$self calls $member"
        }
        return found
    }

    private companion object {
        val OWN_PACKAGES = listOf("dev/mooner/starlight/", "dev/mooner/configdsl/")
        val FORBIDDEN_PERMISSIONS = setOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.INSTALL_PACKAGES",
            "android.permission.REQUEST_DELETE_PACKAGES",
        )
        val FORBIDDEN_CLASSES = listOf(
            "dalvik/system/DexClassLoader",
            "dalvik/system/PathClassLoader",
            "dalvik/system/InMemoryDexClassLoader",
            "dalvik/system/BaseDexClassLoader",
            "dalvik/system/DelegateLastClassLoader",
            "dalvik/system/DexFile",
            "android/content/pm/PackageInstaller",
        )
        val FORBIDDEN_MEMBERS = setOf(
            "java/lang/System.load",
            "java/lang/System.loadLibrary",
            "java/lang/Runtime.load",
            "java/lang/Runtime.loadLibrary",
            "java/lang/Runtime.exec",
            "java/lang/ProcessBuilder.start",
        )
        val FORBIDDEN_STRINGS = setOf("application/vnd.android.package-archive")
    }
}
