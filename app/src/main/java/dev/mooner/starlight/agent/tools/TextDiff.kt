package dev.mooner.starlight.agent.tools

/**
 * Line diffs that show the user what a tool changes, as unified-diff text: `@@` hunk headers, then lines starting with
 * ' ', '-' or '+'. Line endings don't count, since a file may use CRLF while the model writes LF.
 */
object TextDiff {

    enum class Kind { SAME, REMOVED, ADDED }

    class Line(val kind: Kind, val text: String)

    /** [old] to [new] in hunks with [context] lines around each change, cut at [maxChars]; null when nothing changed. */
    fun unified(old: String, new: String, context: Int = CONTEXT, maxChars: Int = MAX_CHARS): String? {
        val lines = lines(split(old), split(new))
        val changed = lines.indices.filter { lines[it].kind != Kind.SAME }
        if (changed.isEmpty())
            return null
        // Changes closer than twice the context share a hunk.
        val hunks = mutableListOf<IntRange>()
        var first = changed.first()
        var last = first
        for (index in changed.drop(1)) {
            if (index - last > 2 * context) {
                hunks += maxOf(0, first - context)..minOf(lines.lastIndex, last + context)
                first = index
            }
            last = index
        }
        hunks += maxOf(0, first - context)..minOf(lines.lastIndex, last + context)
        // The line number each line has in the old and the new text.
        val oldAt = IntArray(lines.size)
        val newAt = IntArray(lines.size)
        var oldLine = 1
        var newLine = 1
        for (i in lines.indices) {
            oldAt[i] = oldLine
            newAt[i] = newLine
            if (lines[i].kind != Kind.ADDED) oldLine++
            if (lines[i].kind != Kind.REMOVED) newLine++
        }
        val out = StringBuilder()
        var shown = 0
        for (range in hunks) {
            val hunk = lines.subList(range.first, range.last + 1)
            out.append("@@ -").append(oldAt[range.first]).append(',').append(hunk.count { it.kind != Kind.ADDED })
                .append(" +").append(newAt[range.first]).append(',').append(hunk.count { it.kind != Kind.REMOVED })
                .append(" @@\n")
            for (line in hunk) {
                if (out.length > maxChars) {
                    out.append("… ").append(changed.size - shown).append(" more changed lines")
                    return out.toString()
                }
                out.append(PREFIX.getValue(line.kind)).append(line.text).append('\n')
                if (line.kind != Kind.SAME)
                    shown++
            }
        }
        return out.toString().trimEnd()
    }

    /** [a] to [b] line by line: the same start and end as they are, the middle by its longest common subsequence. */
    fun lines(a: List<String>, b: List<String>): List<Line> {
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start])
            start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
            endA--
            endB--
        }
        val out = ArrayList<Line>(maxOf(a.size, b.size) + 4)
        for (i in 0 until start)
            out += Line(Kind.SAME, a[i])
        val middleA = a.subList(start, endA)
        val middleB = b.subList(start, endB)
        if (middleA.size.toLong() * middleB.size <= MAX_CELLS) {
            out += common(middleA, middleB)
        } else {
            // Too large a change to align line by line: the old part goes, the new part comes.
            middleA.mapTo(out) { Line(Kind.REMOVED, it) }
            middleB.mapTo(out) { Line(Kind.ADDED, it) }
        }
        for (i in endA until a.size)
            out += Line(Kind.SAME, a[i])
        return out
    }

    private fun common(a: List<String>, b: List<String>): List<Line> {
        val n = a.size
        val m = b.size
        val width = m + 1
        // table[i * width + j]: the longest common subsequence of a[i..] and b[j..].
        val table = IntArray((n + 1) * width)
        for (i in n - 1 downTo 0)
            for (j in m - 1 downTo 0)
                table[i * width + j] = if (a[i] == b[j]) table[(i + 1) * width + j + 1] + 1
                    else maxOf(table[(i + 1) * width + j], table[i * width + j + 1])
        val out = ArrayList<Line>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out += Line(Kind.SAME, a[i]); i++; j++ }
                table[(i + 1) * width + j] >= table[i * width + j + 1] -> { out += Line(Kind.REMOVED, a[i]); i++ }
                else -> { out += Line(Kind.ADDED, b[j]); j++ }
            }
        }
        while (i < n) out += Line(Kind.REMOVED, a[i++])
        while (j < m) out += Line(Kind.ADDED, b[j++])
        return out
    }

    /** The lines of [text]; a final line break ends the last line rather than starting an empty one. */
    private fun split(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n').map { it.removeSuffix("\r") }

    private val PREFIX = mapOf(Kind.SAME to ' ', Kind.REMOVED to '-', Kind.ADDED to '+')

    private const val CONTEXT = 2
    const val MAX_CHARS = 8_000
    /** Lines of the old middle times lines of the new one; above it a change shows as a whole replacement. */
    private const val MAX_CELLS = 1_000_000L
}
