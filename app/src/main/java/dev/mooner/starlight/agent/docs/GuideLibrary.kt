package dev.mooner.starlight.agent.docs

import android.content.res.AssetManager

/**
 * The guides: hand-written Markdown on concepts no single declaration holds (docs/agent-docs.md §3.4), read by
 * `guide:` references and the `read_guide` tool. The app's, and the loaded plugins' (§9).
 */
class GuideLibrary(pages: List<Guide>) {

    class Guide(
        /** The path under `guides/` without `.md`, such as `engines/neonjs`. */
        val id: String,
        val title: String,
        val summary: String,
        val body: String,
        /** `starlight`, or the ID of the plugin whose package has it. */
        val module: String = MODULE,
    ) {
        /** How references and `read_guide` name it: [id] for the app's, `<plugin ID>:<id>` for a plugin's. */
        val ref: String
            get() = if (module == MODULE) id else "$module:$id"

        /** Each heading's anchor, with the text that follows it up to the next heading of its level or above. */
        val sections: Map<String, String> by lazy { sectionsOf(body) }
    }

    private val pages: Map<String, Guide> = LinkedHashMap<String, Guide>().apply {
        // The app's guides come first, so a plugin can't replace one.
        for (guide in pages)
            putIfAbsent(guide.ref, guide)
    }

    /** The app's guides, then the plugins'. */
    val guides: List<Guide>
        get() = pages.values.sortedWith(compareBy<Guide> { it.module != MODULE }.thenBy(Guide::ref))

    /** These guides and [more], such as a plugin's. */
    operator fun plus(more: List<Guide>): GuideLibrary =
        if (more.isEmpty()) this else GuideLibrary(pages.values + more)

    fun guide(ref: String): Guide? =
        pages[parse(ref).first]

    /**
     * A guide or one of its sections, by `<id>[#anchor]`, `<plugin ID>:<id>[#anchor]`, or either as a `guide:`
     * reference; `guide:starlight:<id>` is the app's. `null` if there is no such guide or anchor.
     */
    fun read(ref: String): String? {
        val (key, anchor) = parse(ref)
        val guide = pages[key] ?: return null
        if (anchor == null)
            return "# ${guide.title}\n${guide.summary}\n\n${guide.body.trim()}"
        val section = guide.sections[anchor] ?: return null
        return "# ${guide.title} › ${section.lineSequence().first().trimStart('#', ' ')}\n\n${section.trim()}"
    }

    /** Whether [ref] names an existing guide, and anchor if it has one. */
    fun resolves(ref: String): Boolean {
        val (key, anchor) = parse(ref)
        val guide = pages[key] ?: return false
        return anchor == null || anchor in guide.sections
    }

    /** The one line shown for [ref] in lists: its guide's summary, or for a section the guide's and its titles. */
    fun summaryOf(ref: String): String? {
        val (key, anchor) = parse(ref)
        val guide = pages[key] ?: return null
        val heading = anchor?.let { guide.sections[it]?.lineSequence()?.first()?.trimStart('#', ' ') }
        return if (heading == null) guide.summary else "${guide.title}: $heading"
    }

    /** The [Guide.ref] [ref] names, and its anchor. Without a module, it is the app's. */
    private fun parse(ref: String): Pair<String, String?> {
        var rest = ref.trim().removePrefix(PREFIX)
        val module = moduleOf(rest)
        if (module != null)
            rest = rest.substringAfter(':')
        val id = rest.substringBefore('#').removeSuffix(".md").trim('/')
        val anchor = rest.substringAfter('#', missingDelimiterValue = "").ifEmpty { null }
        return (if (module == null || module == MODULE) id else "$module:$id") to anchor
    }

    companion object {

        const val PREFIX = "guide:"
        const val MODULE = "starlight"
        const val ROOT = "agent/guides"

        /** `guide:` followed by an ID that names no module: `guide:setup`, not `guide:starlight:projects`. */
        private val UNQUALIFIED = Regex("""\b${Regex.escape(PREFIX)}(?![A-Za-z0-9._-]+:[A-Za-z0-9])""")

        /** The module before the ID in a reference without its `guide:`, if it names one. Plugin IDs have no `/` or `#`. */
        private fun moduleOf(ref: String): String? =
            ref.substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() && '/' !in it && '#' !in it }

        /**
         * [text] with each `guide:` reference that names no module made one of [module]'s: in a plugin's docs and
         * guides, `guide:setup` is the plugin's own guide, and `guide:starlight:projects` the app's.
         */
        fun qualify(text: String, module: String): String =
            if (module == MODULE) text else text.replace(UNQUALIFIED, Regex.escapeReplacement("$PREFIX$module:"))

        /** The guides in the app's assets. */
        fun load(assets: AssetManager): GuideLibrary =
            GuideLibrary(walk(assets, ROOT).mapNotNull { path ->
                parse(path.removePrefix("$ROOT/").removeSuffix(".md"), assets.open(path).bufferedReader().use { it.readText() })
            })

        private fun walk(assets: AssetManager, dir: String): List<String> =
            (assets.list(dir) ?: emptyArray()).flatMap { name ->
                val path = "$dir/$name"
                if (name.endsWith(".md")) listOf(path) else walk(assets, path)
            }

        /** A guide from its file's text: front matter with `title` and `summary`, then Markdown. */
        fun parse(id: String, text: String, module: String = MODULE): Guide? {
            if (!text.startsWith("---"))
                return null
            val header = text.removePrefix("---").substringBefore("\n---")
            val fields = header.lines()
                .mapNotNull { line ->
                    val key = line.substringBefore(':', "").trim()
                    if (key.isEmpty()) null else key to line.substringAfter(':').trim()
                }
                .toMap()
            val body = text.removePrefix("---").substringAfter("\n---").substringAfter('\n')
            return Guide(id, fields["title"].orEmpty(), fields["summary"].orEmpty(), body, module)
        }

        /** A heading's anchor: lowercased, spaces as `-`, characters other than letters, digits and `-` removed. */
        fun slug(heading: String): String =
            heading.trim().lowercase().replace(' ', '-').filter { it.isLetterOrDigit() || it == '-' }

        private fun sectionsOf(body: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            val lines = body.lines()
            var inCode = false
            val headings = mutableListOf<Pair<Int, Int>>()
            for ((i, line) in lines.withIndex()) {
                if (line.trimStart().startsWith("```"))
                    inCode = !inCode
                val level = line.takeWhile { it == '#' }.length
                if (!inCode && level in 1..6 && line.getOrNull(level) == ' ')
                    headings += i to level
            }
            for ((index, heading) in headings.withIndex()) {
                val (start, level) = heading
                val end = headings.drop(index + 1).firstOrNull { it.second <= level }?.first ?: lines.size
                result.putIfAbsent(slug(lines[start].substring(level)), lines.subList(start, end).joinToString("\n"))
            }
            return result
        }
    }
}
