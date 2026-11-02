package dev.mooner.starlight.agent.tools

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.net.URI

/** Reads web pages for web_fetch: the readable text of a page, without its scripts, menus and styling. */
internal object WebPages {

    class Page(
        val url: String,
        val status: Int,
        val type: String,
        val title: String?,
        val text: String,
        val links: List<Pair<String, String>>,
    )

    /** Fetches [url], following redirects; throws [IllegalArgumentException] for an address it won't read. */
    fun fetch(url: String): Page {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        require(uri != null && uri.scheme?.lowercase() in SCHEMES && !uri.host.isNullOrBlank()) {
            "Only http and https addresses can be read: $url"
        }
        val response = Jsoup.connect(uri.toString())
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MILLIS)
            .maxBodySize(MAX_BYTES)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .followRedirects(true)
            .execute()
        val type = response.contentType().orEmpty()
        val finalUrl = response.url().toString()
        return when {
            "html" in type || type.isEmpty() && response.body().trimStart().startsWith("<") ->
                read(response.parse(), finalUrl, response.statusCode(), type)
            type.startsWith("text/") || "json" in type || "xml" in type || "javascript" in type || type.isEmpty() ->
                Page(finalUrl, response.statusCode(), type, null, response.body(), emptyList())
            else -> throw IllegalArgumentException("$finalUrl is $type, which can't be read as text")
        }
    }

    /** The readable part of [document]: its main content as text, block by block, and the links in it. */
    fun read(document: Document, url: String, status: Int = 200, type: String = "text/html"): Page {
        document.select(NOISE).remove()
        val root = document.selectFirst("main, article, [role=main]") ?: document.body()
        val links = root.select("a[href]").mapNotNull { a ->
            val href = a.absUrl("href")
            val label = a.text().trim()
            if (href.startsWith("http") && label.isNotEmpty()) label.take(MAX_LINK_TEXT) to href else null
        }.distinctBy { it.second }.take(MAX_LINKS)
        return Page(url, status, type, document.title().trim().ifEmpty { null }, text(root), links)
    }

    /** The text of [root] with a line break after each block, headings marked with #, list items with -. */
    fun text(root: Element): String {
        val out = StringBuilder()
        fun newLine() {
            if (out.isNotEmpty() && out.last() != '\n')
                out.append('\n')
        }
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when (node) {
                    is TextNode -> out.append(if (inPre(node)) node.wholeText else node.text())
                    is Element -> when {
                        node.normalName() == "br"    -> out.append('\n')
                        node.normalName() in HEADINGS -> {
                            newLine()
                            out.append("#".repeat(node.normalName().substring(1).toInt())).append(' ')
                        }
                        node.normalName() == "li"    -> {
                            newLine()
                            out.append("- ")
                        }
                        node.isBlock                 -> newLine()
                    }
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.isBlock)
                    newLine()
            }
        }, root)
        // Blank and space-only lines collapse into one blank line between blocks.
        return out.lines().map(String::trimEnd)
            .fold(mutableListOf<String>()) { lines, line ->
                if (line.isNotBlank() || lines.lastOrNull()?.isNotBlank() == true) lines += line
                lines
            }.joinToString("\n").trim()
    }

    private fun inPre(node: Node): Boolean {
        var parent = node.parent()
        while (parent is Element) {
            if (parent.normalName() == "pre")
                return true
            parent = parent.parent()
        }
        return false
    }

    private val SCHEMES = setOf("http", "https")
    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private const val NOISE = "script, style, noscript, svg, iframe, template, nav, footer, form, button"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) StarLight-Agent"
    private const val TIMEOUT_MILLIS = 15_000
    private const val MAX_BYTES = 3 * 1024 * 1024
    private const val MAX_LINKS = 40
    private const val MAX_LINK_TEXT = 80
}
