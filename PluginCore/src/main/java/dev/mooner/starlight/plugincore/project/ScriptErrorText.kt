package dev.mooner.starlight.plugincore.project

import java.io.File

/**
 * How a script's error reads in a log: what it is and where, on the first line, then the script's own stack. An
 * engine's exception says what went wrong; where it did is in properties its text doesn't always show (NeonJS's
 * `line`, `column`, `sourceName` and `jsStack`), so a log of the text alone left the author to guess.
 *
 * The properties are looked up by name, so this serves any engine, a plugin's language included, without knowing
 * its classes.
 */
internal object ScriptErrorText {

    /** The error as text. [projectDir] is where the script's files are, to name one by its path inside the project. */
    fun of(error: Throwable, projectDir: File? = null): String {
        val text = error.toString()
        val chain = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(MAX_DEPTH).toList()
        val place = chain.firstNotNullOfOrNull { placeOf(it, projectDir) }
        // An engine that already names the line in its text, as Rhino does ("(name#12)"), isn't told twice.
        val first = if (place == null || place.isNamedIn(text)) text else "$text (${place.text})"
        val stack = chain.firstNotNullOfOrNull { call(it, "getJsStack") as? String }
            ?.lineSequence()?.map(String::trimEnd)?.filter(String::isNotBlank)?.take(MAX_STACK_LINES)?.joinToString("\n")
        return if (stack.isNullOrEmpty()) first else "$first\n$stack"
    }

    private class Place(val text: String, val line: Int) {
        fun isNamedIn(message: String): Boolean =
            Regex("[#:]$line(\\D|$)").containsMatchIn(message)
    }

    private fun placeOf(error: Throwable, projectDir: File?): Place? {
        val line = (call(error, "getLine") as? Int)?.takeIf { it > 0 } ?: return null
        val column = (call(error, "getColumn") as? Int)?.takeIf { it > 0 }
        val source = (call(error, "getSourceName") as? String)?.let { nameOf(it, projectDir) }
        return Place(listOfNotNull(source, line, column).joinToString(":"), line)
    }

    /** A source inside the project by its path there, another by its file name: not the device's whole path. */
    private fun nameOf(source: String, projectDir: File?): String {
        val root = projectDir?.let { runCatching { it.canonicalPath }.getOrNull() }
        return if (root != null && source.startsWith(root + File.separator))
            source.substring(root.length + 1)
        else
            source.substringAfterLast(File.separatorChar)
    }

    private fun call(target: Any, name: String): Any? =
        runCatching { target.javaClass.getMethod(name).invoke(target) }.getOrNull()

    private const val MAX_DEPTH = 8
    private const val MAX_STACK_LINES = 10
}
