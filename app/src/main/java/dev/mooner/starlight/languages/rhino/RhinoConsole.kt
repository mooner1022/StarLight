/*
 * RhinoConsole.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * The `console` of a Rhino project, with MessengerBot's thirteen members: `log`, `info`, `debug`, `warn`, `error`,
 * `clear`, `time`, `timeEnd`, `timeLog`, `count`, `countReset`, `assert` and `table`.
 *
 * Each writes one entry to the project's log through [sink]; none shows a toast, and there are no format
 * specifiers: the arguments are written as text and joined by spaces.
 */
internal class RhinoConsole(
    scope: Scriptable,
    private val onClear: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sink: (Level, String) -> Unit,
) : ScriptableObject() {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val timers = HashMap<String, Long>()
    private val counters = HashMap<String, Int>()

    override fun getClassName(): String = "console"

    init {
        parentScope = scope
        prototype = getObjectPrototype(scope)

        member("log", 0) { out(Level.INFO, joined(it)) }
        member("info", 0) { out(Level.INFO, joined(it)) }
        member("debug", 0) { out(Level.DEBUG, joined(it)) }
        member("warn", 0) { out(Level.WARN, joined(it)) }
        member("error", 0) { out(Level.ERROR, joined(it)) }
        member("clear", 0) { onClear(); Unit }
        member("time", 0) { timers[label(it)] = clock(); Unit }
        member("timeEnd", 0) { args ->
            val label = label(args)
            val start = timers.remove(label)
            if (start == null) out(Level.WARN, "Timer '$label' does not exist")
            else out(Level.INFO, "$label: ${clock() - start}ms")
        }
        member("timeLog", 0) { args ->
            val label = label(args)
            val start = timers[label]
            if (start == null)
                out(Level.WARN, "Timer '$label' does not exist")
            else {
                // The label is repeated in the text, as MessengerBot's does when there is more than one argument.
                val extra = if (args.size > 1) " " + joined(args) else ""
                out(Level.INFO, "$label: ${clock() - start}ms$extra")
            }
        }
        member("count", 0) { args ->
            val label = label(args)
            val n = (counters[label] ?: 0) + 1
            counters[label] = n
            out(Level.INFO, "$label: $n")
        }
        member("countReset", 0) { counters.remove(label(it)); Unit }
        member("assert", 0) { args ->
            if (args.isNotEmpty() && !isTruthy(args[0]))
                out(Level.ERROR, "Assertion failed: " + joined(args))
            else
                Unit
        }
        member("table", 0) { args ->
            if (args.isNotEmpty())
                out(Level.INFO, table(args[0]))
            else
                Unit
        }
    }

    private fun member(name: String, arity: Int, body: (args: Array<Any?>) -> Any?) {
        defineProperty(name, RhinoFunction(this, name, arity) { _, args -> body(args) }, EMPTY)
    }

    private fun out(level: Level, text: String) {
        sink(level, text)
    }

    private fun label(args: Array<Any?>): String =
        if (args.isEmpty()) "default" else labelOf(args[0])

    companion object {

        private const val MAX_KEYS = 5
        private const val FRAME = "─────────────────"

        /** A label as text: `undefined` is `"undefined"`, as the label of `console.count(undefined)`. */
        internal fun labelOf(value: Any?): String =
            if (value === Undefined.instance) "undefined" else text(value, quote = false)

        /** The arguments of a call as one line: each as [text], joined by single spaces. */
        internal fun joined(args: Array<Any?>): String =
            args.joinToString(" ") { text(it, quote = false) }

        /**
         * A condition as `console.assert` reads it: a boolean as it is, a number is true unless `0`, the strings
         * `"true"` and `"false"` count, and anything else, an object or `undefined` included, is false.
         */
        internal fun isTruthy(value: Any?): Boolean = when (value) {
            is Boolean      -> value
            is Number       -> value.toDouble() != 0.0
            is CharSequence -> value.toString() == "true"
            else            -> false
        }

        /** Whether [value] is a JS error object; Rhino's own class for them isn't public. */
        private fun isError(value: Any?): Boolean =
            value is Scriptable && value.className == "Error"

        /**
         * [value] as text for a log line.
         *
         * `null`, `undefined` and a missing property (`Scriptable.NOT_FOUND`) are `null`; strings are as they are (in
         * quotes when [quote] and inside an object); an array is `[1, 2]`; a plain object is `{a: 1, b: "x"}` for its first five keys, with `, ...` when it has
         * more, and `[object Object]` for an object or array held in it. A function or an error is its own text.
         */
        internal fun text(value: Any?, quote: Boolean): String = when {
            value == null || value === Undefined.instance || value === Scriptable.NOT_FOUND -> "null"
            value is CharSequence -> if (quote) "\"$value\"" else value.toString()
            value is Boolean      -> value.toString()
            value is Number       -> Context.toString(value)
            value is Wrapper      -> value.unwrap().toString()
            value is NativeArray  -> arrayText(value)
            value is Function || isError(value) -> Context.toString(value)
            value is Scriptable   -> objectText(value)
            else                  -> value.toString()
        }

        private fun arrayText(array: NativeArray): String {
            val parts = ArrayList<String>()
            for (i in 0 until array.length.toInt())
                parts += text(ScriptableObject.getProperty(array, i), quote = false)
            return parts.joinToString(", ", "[", "]")
        }

        private fun keysOf(obj: Scriptable): List<Any> =
            (if (obj is ScriptableObject) obj.getIds() else obj.ids).toList()

        private fun valueOf(obj: Scriptable, key: Any): Any? =
            if (key is Int) ScriptableObject.getProperty(obj, key) else ScriptableObject.getProperty(obj, key.toString())

        private fun objectText(obj: Scriptable): String {
            val keys = keysOf(obj)
            if (keys.isEmpty())
                return "{}"
            val parts = keys.take(MAX_KEYS).map { key ->
                val member = valueOf(obj, key)
                val shown = when {
                    member is CharSequence -> "\"$member\""
                    member is Scriptable && member !is Wrapper -> "[object Object]"
                    else -> text(member, quote = false)
                }
                "$key: $shown"
            }
            return parts.joinToString(", ", "{", (if (keys.size > MAX_KEYS) ", ..." else "") + "}")
        }

        /**
         * `console.table`: one `index: value` line per item of an array, or `key: value` per member of an object, in a
         * fixed frame; anything else as [text].
         */
        internal fun table(value: Any?): String {
            val lines: List<String> = when {
                value == null || value === Undefined.instance -> return "null"
                value is NativeArray ->
                    (0 until value.length.toInt()).map { "$it: " + text(ScriptableObject.getProperty(value, it), quote = false) }
                value is Scriptable && value !is Wrapper && value !is Function && !isError(value) -> {
                    val keys = keysOf(value)
                    if (keys.isEmpty())
                        return text(value, quote = false)
                    keys.map { "$it: " + text(valueOf(value, it), quote = false) }
                }
                else -> return text(value, quote = false)
            }
            return "┌$FRAME┐\n│ " + lines.joinToString("\n│ ") + " \n└$FRAME┘"
        }
    }
}
