package dev.mooner.starlight.agent.docs

import java.io.File
import java.util.zip.ZipFile

/**
 * A plugin's docs, from its package (docs/agent-docs.md §9): the doc file `starlight/agent/<plugin ID>.api.json`, which
 * the docs processor writes when the StarLight Gradle plugin runs it with KSP, and the guides under
 * `assets/agent/guides/`. A `guide:` reference without a module in them names the plugin's own guide.
 */
class PluginDocs(
    val pluginId: String,
    /** `null` when the package has none, or one of a format this app doesn't read. */
    val file: DocFile?,
    val guides: List<GuideLibrary.Guide>,
) {

    companion object {

        const val GUIDES = "assets/${GuideLibrary.ROOT}/"

        /** Reads the docs in [packageFile], the `.slp` of plugin [pluginId]. */
        fun read(packageFile: File, pluginId: String): PluginDocs = ZipFile(packageFile).use { zip ->
            fun text(entry: java.util.zip.ZipEntry) = zip.getInputStream(entry).bufferedReader().use { it.readText() }

            val file = zip.getEntry(DocFiles.resourcePath(pluginId))
                ?.let { entry -> runCatching { DocFiles.parse(text(entry)) }.getOrNull() }
                ?.qualified(pluginId)
            val guides = zip.entries().toList()
                .filter { !it.isDirectory && it.name.startsWith(GUIDES) && it.name.endsWith(".md") }
                .mapNotNull { entry ->
                    val id = entry.name.removePrefix(GUIDES).removeSuffix(".md")
                    GuideLibrary.parse(id, text(entry), module = pluginId)?.qualified()
                }
            PluginDocs(pluginId, file, guides)
        }

        private fun GuideLibrary.Guide.qualified() =
            GuideLibrary.Guide(id, title, GuideLibrary.qualify(summary, module), GuideLibrary.qualify(body, module), module)

        private fun DocFile.qualified(module: String) =
            copy(classes = classes.mapValues { (_, cls) ->
                cls.copy(
                    doc = cls.doc?.qualified(module),
                    members = cls.members.mapValues { (_, member) -> member.copy(doc = member.doc?.qualified(module)) },
                )
            })

        private fun DocText.qualified(module: String): DocText {
            fun String.q() = GuideLibrary.qualify(this, module)
            return copy(
                summary = summary.q(),
                details = details?.q(),
                params = params.mapValues { it.value.q() },
                returns = returns?.q(),
                throws = throws.mapValues { it.value.q() },
                see = see.map { it.q() },
            )
        }
    }
}
