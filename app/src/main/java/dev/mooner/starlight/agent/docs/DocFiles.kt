package dev.mooner.starlight.agent.docs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The doc files the docs processor writes at build time, `starlight/agent/<module>.api.json`: KDoc and Kotlin
 * signatures keyed by JVM name and descriptor. See docs/agent-docs.md §4.
 */

@Serializable
data class DocFile(
    val format: Int,
    val module: String,
    /**
     * What the module documents: packages, with their subpackages, and single classes, with their nested ones. The
     * closure stops outside them.
     */
    val packages: List<String> = emptyList(),
    /** By binary class name, as `Class.getName()` gives it. */
    val classes: Map<String, DocClass> = emptyMap(),
)

@Serializable
data class DocClass(
    val kind: String,
    val doc: DocText? = null,
    /** By JVM name and descriptor; `field:<name>` for fields. */
    val members: Map<String, DocMember> = emptyMap(),
)

@Serializable
data class DocMember(
    /** `constructor`, `function`, `getter`, `setter` or `field`. */
    val kind: String,
    val name: String,
    val params: List<DocParam> = emptyList(),
    /** The Kotlin type, as written: `kotlin.String?`, `kotlin.Array<a.b.Room>`. */
    val returns: String? = null,
    val mutable: Boolean = false,
    val doc: DocText? = null,
)

@Serializable
data class DocParam(
    val name: String,
    val type: String,
    val optional: Boolean = false,
    val vararg: Boolean = false,
)

@Serializable
data class DocText(
    val summary: String = "",
    val details: String? = null,
    val params: Map<String, String> = emptyMap(),
    val returns: String? = null,
    val throws: Map<String, String> = emptyMap(),
    val since: String? = null,
    val suppress: Boolean = false,
    val see: List<String> = emptyList(),
    /** For a function taking an event name and a listener: the ID prefix of the listener events it takes. */
    val listeners: String? = null,
)

/** The doc files that were read, and what they say about each class. */
class DocFiles(files: List<DocFile>) {

    val modules: List<DocFile> = files

    private val classes: Map<String, DocClass> = HashMap<String, DocClass>().apply {
        // The app's files come first, so a plugin's can't replace their docs.
        for (file in files)
            for ((name, cls) in file.classes)
                putIfAbsent(name, cls)
    }

    private val packages: List<String> = files.flatMap(DocFile::packages)

    /**
     * The classes of files that name no packages. A plugin's does, as the StarLight Gradle plugin runs the docs
     * processor without `starlight.docs.packages`, and lists every public class of the plugin.
     */
    private val listed: Set<String> = files.filter { it.packages.isEmpty() }.flatMapTo(hashSetOf()) { it.classes.keys }

    /** These files and [more], such as the loaded plugins'. */
    operator fun plus(more: List<DocFile>): DocFiles =
        if (more.isEmpty()) this else DocFiles(modules + more)

    fun docOf(cls: Class<*>): DocClass? =
        classes[cls.name]

    /**
     * The doc entries of [cls]'s members, with its companion's: an `@JvmStatic` member is documented on the companion
     * but reflected on the class.
     */
    fun membersOf(cls: Class<*>): Map<String, DocMember> {
        val own = classes[cls.name]?.members.orEmpty()
        val companion = classes[cls.name + "\$Companion"]?.members ?: return own
        return companion + own
    }

    /** Whether [cls] is inside a documented package, or is a documented class: described, and followed in a closure. */
    fun isDocumented(cls: Class<*>): Boolean {
        if (cls.name in listed)
            return true
        val packageName = cls.name.substringBeforeLast('.', "")
        return packages.any { packageName == it || packageName.startsWith("$it.") || cls.name == it || cls.name.startsWith("$it\$") }
    }

    companion object {

        /** The format this app reads; files of another one are skipped. */
        const val FORMAT = 1

        /** The modules of the app itself. */
        val CORE_MODULES = listOf("starlight", "plugincore")

        private val json = Json { ignoreUnknownKeys = true }

        fun resourcePath(module: String): String =
            "starlight/agent/$module.api.json"

        /** Reads the doc file of each of [modules] that [loader] has, skipping missing or unreadable ones. */
        fun load(loader: ClassLoader, modules: List<String> = CORE_MODULES): DocFiles =
            DocFiles(modules.mapNotNull { module ->
                runCatching {
                    loader.getResourceAsStream(resourcePath(module))?.bufferedReader()?.use { parse(it.readText()) }
                }.getOrNull()
            })

        fun parse(text: String): DocFile? =
            json.decodeFromString(DocFile.serializer(), text).takeIf { it.format == FORMAT }
    }
}
