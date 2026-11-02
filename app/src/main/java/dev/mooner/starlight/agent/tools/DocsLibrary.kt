package dev.mooner.starlight.agent.tools

import android.content.res.AssetManager

/**
 * StarLight's user documentation (app and NeonJS, in Korean), bundled from starlight.mooner.dev so that the agent can
 * look up script APIs instead of guessing them.
 */
class DocsLibrary(private val assets: AssetManager) {

    class Page(val path: String, val title: String, val description: String)

    /** Every page, with the title and description from its front matter. */
    val pages: List<Page> by lazy {
        walk(ROOT).sorted().map { path ->
            val header = frontMatter(read(path))
            Page(path.removePrefix("$ROOT/").removeSuffix(".md"), header["title"].orEmpty(), header["description"].orEmpty())
        }
    }

    /** A page's text without its front matter, by a path from [pages] such as `app/reference/events`. */
    fun page(path: String): String {
        val clean = path.trim().trim('/').removeSuffix(".md").removePrefix("docs/")
        if (clean.contains(".."))
            throw ToolException("No documentation page: $path")
        val text = try {
            read("$ROOT/$clean.md")
        } catch (e: java.io.IOException) {
            try {
                read("$ROOT/$clean/index.md")
            } catch (e: java.io.IOException) {
                throw ToolException("No documentation page: $path. Call read_docs without a path to list the pages.")
            }
        }
        val header = frontMatter(text)
        val body = text.substringAfter("\n---", text).let { if (text.startsWith("---")) it.substringAfter('\n') else text }
        return "# ${header["title"].orEmpty()}\n${header["description"].orEmpty()}\n\n${body.trim()}"
    }

    private fun walk(dir: String): List<String> =
        (assets.list(dir) ?: emptyArray()).flatMap { name ->
            val path = "$dir/$name"
            if (name.endsWith(".md")) listOf(path) else walk(path)
        }

    private fun read(path: String): String =
        assets.open(path).bufferedReader().use { it.readText() }

    private fun frontMatter(text: String): Map<String, String> {
        if (!text.startsWith("---"))
            return emptyMap()
        return text.removePrefix("---").substringBefore("\n---").lines()
            .mapNotNull { line ->
                val key = line.substringBefore(':', "").trim()
                if (key.isEmpty()) null else key to line.substringAfter(':').trim()
            }
            .toMap()
    }

    private companion object {
        const val ROOT = "agent/docs"
    }
}
