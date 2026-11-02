package dev.mooner.starlight.agent.tools

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
}
