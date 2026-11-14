package dev.mooner.starlight.languages

import java.io.File

/**
 * Where a script error is, from the engine's exception in the cause chain: NeonJS's syntax exception (`getLine`,
 * `getColumn`, `getSourceName`) or Rhino's `RhinoException` (`lineNumber`, `columnNumber`, `sourceName`). Looked up by
 * name, so that one helper serves every engine, plugin languages included.
 */
internal object ScriptErrors {

    class Location(val source: String?, val line: Int, val column: Int?)

    private val GETTERS = listOf(
        Triple("getLine", "getColumn", "getSourceName"),
        Triple("lineNumber", "columnNumber", "sourceName"),
    )

    fun locate(error: Throwable): Location? =
        generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
            .take(MAX_DEPTH)
            .firstNotNullOfOrNull(::locationOf)

    /**
     * The error as it reads in a log or an alert: what it is and where, on the first line, then the script's own
     * stack, cut to a few lines. An engine's text doesn't always say where: NeonJS keeps the line, the column, the
     * file and the stack in properties of its exception. [projectDir] names a file by its path inside the project.
     */
    fun describe(error: Throwable, projectDir: File? = null): String {
        val text = error.toString()
        val at = locate(error)
        val place = at?.let { listOfNotNull(it.source?.let { source -> nameOf(source, projectDir) }, it.line, it.column).joinToString(":") }
        // An engine that already names the line in its text, as Rhino does ("(name#12)"), isn't told twice.
        val first = if (at == null || Regex("[#:]${at.line}(\\D|$)").containsMatchIn(text)) text else "$text ($place)"
        val stack = chain(error).firstNotNullOfOrNull { call(it, "getJsStack") as? String }
            ?.lineSequence()?.map(String::trimEnd)?.filter(String::isNotBlank)?.take(MAX_STACK_LINES)?.joinToString("\n")
        return if (stack.isNullOrEmpty()) first else "$first\n$stack"
    }

    /** The file of the project that [location] is in, or null when it names none of its files. */
    fun fileOf(location: Location, projectDir: File): File? {
        val source = location.source ?: return null
        val root = runCatching { projectDir.canonicalFile }.getOrNull() ?: return null
        val file = File(source).let { if (it.isAbsolute) it else File(root, source) }
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return canonical.takeIf { it.isFile && it.path.startsWith(root.path + File.separator) }
    }

    /** A source inside the project by its path there, another by its file name: not the device's whole path. */
    private fun nameOf(source: String, projectDir: File?): String {
        val root = projectDir?.let { runCatching { it.canonicalPath }.getOrNull() }
        return if (root != null && source.startsWith(root + File.separator))
            source.substring(root.length + 1)
        else
            source.substringAfterLast(File.separatorChar)
    }

    private fun chain(error: Throwable): List<Throwable> =
        generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(MAX_DEPTH).toList()

    /** The innermost message, which is usually the engine's own description of the error. */
    fun message(error: Throwable): String {
        val chain = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(MAX_DEPTH).toList()
        return chain.lastOrNull { !it.message.isNullOrBlank() }?.message ?: error.toString()
    }

    private fun locationOf(error: Throwable): Location? {
        for ((lineName, columnName, sourceName) in GETTERS) {
            val line = call(error, lineName) as? Int ?: continue
            if (line <= 0)
                continue
            val column = (call(error, columnName) as? Int)?.takeIf { it > 0 }
            return Location(call(error, sourceName) as? String, line, column)
        }
        return null
    }

    private fun call(target: Any, name: String): Any? =
        runCatching { target.javaClass.getMethod(name).invoke(target) }.getOrNull()

    private const val MAX_DEPTH = 8
    private const val MAX_STACK_LINES = 10
}
