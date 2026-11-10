package dev.mooner.starlight.docs.processor

/**
 * A KDoc comment read into the parts agent docs use (see docs/agent-docs.md §3.2).
 *
 * @property summary The first sentence: the one line shown in lists.
 * @property details The rest of the text, as Markdown.
 * @property params `@param` texts by parameter name.
 * @property throws `@throws` (and `@exception`) texts by exception name.
 * @property see The `guide:` references of `@see` tags.
 */
data class KDoc(
    val summary: String,
    val details: String?,
    val params: Map<String, String>,
    val returns: String?,
    val throws: Map<String, String>,
    val since: String?,
    val suppress: Boolean,
    val see: List<String>,
    /**
     * From `@listeners <event ID prefix>`: the function takes an event name, then a listener of that event, for the
     * listener events whose IDs start with the prefix (API2's `bot.on`).
     */
    val listeners: String? = null,
) {

    companion object {

        private const val GUIDE_PREFIX = "guide:"

        private val MARGIN = Regex("^\\s*\\*(\\s|$)")

        /** Reads [raw], a doc comment's text with or without its `*` margins; `null` when there is none. */
        fun parse(raw: String?): KDoc? {
            if (raw.isNullOrBlank())
                return null
            // KSP gives the text without the `*` margins, so a line may start with `**bold**`: margins are stripped
            // only when every line has one, a `*` followed by a space or nothing.
            val margined = raw.lines().filter(String::isNotBlank).all(MARGIN::containsMatchIn)
            val unmargined = if (!margined) raw.lines() else raw.lines().map { line ->
                line.trimStart().removePrefix("*").removePrefix(" ")
            }
            // Only the indentation every line shares goes, so code blocks keep theirs.
            val indent = unmargined.filter(String::isNotBlank).minOfOrNull { it.length - it.trimStart().length } ?: 0
            val lines = unmargined.map { it.drop(indent).trimEnd() }

            val body = StringBuilder()
            val tags = mutableListOf<Pair<String, StringBuilder>>()
            var inCode = false
            for (line in lines) {
                if (line.trimStart().startsWith("```"))
                    inCode = !inCode
                val tag = if (!inCode && line.startsWith("@")) line.substringBefore(' ') else null
                when {
                    tag != null -> tags += tag to StringBuilder(line.removePrefix(tag).trim())
                    tags.isNotEmpty() -> tags.last().second.append('\n').append(line)
                    else -> body.append(line).append('\n')
                }
            }

            val text = links(body.toString().trim())
            val (summary, details) = splitSummary(text)

            val params = LinkedHashMap<String, String>()
            val throws = LinkedHashMap<String, String>()
            var returns: String? = null
            var since: String? = null
            var suppress = false
            var listeners: String? = null
            val see = mutableListOf<String>()
            for ((tag, content) in tags) {
                val value = links(content.toString().trim())
                when (tag) {
                    "@param", "@property" -> named(value)?.let { (name, text) -> params[name] = text }
                    "@return" -> returns = value.ifEmpty { null }
                    "@throws", "@exception" -> named(value)?.let { (name, text) -> throws[name] = text }
                    "@since" -> since = value.ifEmpty { null }
                    "@suppress" -> suppress = true
                    "@listeners" -> listeners = value.substringBefore(' ').ifEmpty { null }
                    "@see" -> value.substringBefore(' ').takeIf { it.startsWith(GUIDE_PREFIX) }?.let(see::add)
                }
            }
            return KDoc(summary, details, params, returns, throws, since, suppress, see, listeners)
        }

        /** The first sentence, and what follows it. A sentence ends at `.`, `!` or `?` before whitespace. */
        internal fun splitSummary(text: String): Pair<String, String?> {
            if (text.isEmpty())
                return "" to null
            val paragraph = text.substringBefore("\n\n")
            val rest = text.substring(paragraph.length).trim()
            val flat = paragraph.replace('\n', ' ')
            var inCode = false
            for (i in flat.indices) {
                val c = flat[i]
                if (c == '`')
                    inCode = !inCode
                if (!inCode && (c == '.' || c == '!' || c == '?') && (i == flat.lastIndex || flat[i + 1] == ' ')) {
                    val summary = flat.substring(0, i + 1).trim()
                    val remainder = flat.substring(i + 1).trim()
                    val details = listOf(remainder, rest).filter(String::isNotEmpty).joinToString("\n\n")
                    return summary to details.ifEmpty { null }
                }
            }
            return flat.trim() to rest.ifEmpty { null }
        }

        /** KDoc links read as code: `[Bot.send]` becomes `` `Bot.send` ``; `[text][Target]` keeps its text. */
        internal fun links(text: String): String {
            val out = StringBuilder()
            var i = 0
            var inCode = false
            while (i < text.length) {
                val c = text[i]
                if (c == '`') {
                    inCode = !inCode
                    out.append(c)
                    i++
                    continue
                }
                if (!inCode && c == '[') {
                    val end = text.indexOf(']', i)
                    val label = if (end > i) text.substring(i + 1, end) else null
                    if (label != null && label.isNotEmpty() && '\n' !in label) {
                        val next = end + 1
                        // [text][Target]: the text stays as it is.
                        if (next < text.length && text[next] == '[') {
                            val targetEnd = text.indexOf(']', next)
                            if (targetEnd > next) {
                                out.append(label)
                                i = targetEnd + 1
                                continue
                            }
                        }
                        // A Markdown link [text](url) is left alone.
                        if (next < text.length && text[next] == '(') {
                            out.append(c)
                            i++
                            continue
                        }
                        out.append('`').append(label).append('`')
                        i = next
                        continue
                    }
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

        private fun named(value: String): Pair<String, String>? {
            val name = value.substringBefore(' ').substringBefore('\n').trim().removeSurrounding("`")
            if (name.isEmpty())
                return null
            return name to value.removePrefix(value.substringBefore(' ').substringBefore('\n')).trim()
        }
    }
}
