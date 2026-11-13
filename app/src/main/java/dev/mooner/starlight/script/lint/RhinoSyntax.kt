/*
 * RhinoSyntax.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.lint

import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.IdeErrorReporter

/**
 * What Rhino's own parser can't read in a script: the one answer to whether Rhino will compile it. Rhino reads less
 * than NeonJS (no classes, default parameters, spread, `async`, `?.`, `??`) and some things NeonJS doesn't (`for each`,
 * `catch (e if …)`), so no other parser can say.
 *
 * The parser goes on after an error, as an editor wants, and what follows from one mistake on a line is left out.
 */
internal object RhinoSyntax {

    /** An error at [start] to [end], offsets in UTF-16 units, with Rhino's message. */
    class Error(val start: Int, val end: Int, val message: String)

    /**
     * @param languageVersion Rhino's, as the project's scripts are compiled with (`Context.VERSION_ES6`…).
     */
    fun errors(source: String, languageVersion: Int): List<Error> {
        val found = ArrayList<Error>()
        val environs = CompilerEnvirons().apply {
            this.languageVersion = languageVersion
            setRecoverFromErrors(true)
            setIdeMode(true)
            isStrictMode = false
        }
        val reporter = object : IdeErrorReporter {
            override fun error(message: String?, sourceName: String?, offset: Int, length: Int) {
                found += Error(offset, offset + maxOf(length, 0), message.orEmpty())
            }

            // Warnings are Rhino's opinions of style: not what stops a script.
            override fun warning(message: String?, sourceName: String?, offset: Int, length: Int) = Unit

            override fun warning(message: String?, sourceName: String?, line: Int, lineSource: String?, lineOffset: Int) = Unit

            override fun error(message: String?, sourceName: String?, line: Int, lineSource: String?, lineOffset: Int) {
                // Not how an IDE-mode parser reports, but an error all the same: at its line's start.
                val start = lineStart(source, line)
                found += Error(start + maxOf(lineOffset, 0), start + maxOf(lineOffset, 0), message.orEmpty())
            }

            override fun runtimeError(message: String?, sourceName: String?, line: Int, lineSource: String?, lineOffset: Int) =
                EvaluatorException(message, sourceName, line, lineSource, lineOffset)
        }
        try {
            Parser(environs, reporter).parse(source, "script", 1)
        } catch (_: EvaluatorException) {
            // Rhino gave up: what it reported until then stands.
        }
        val units = unitOffsets(source)
        val lines = HashSet<Int>()
        return found
            .map { Error(units(it.start), maxOf(units(it.end), units(it.start)), it.message) }
            .sortedBy { it.start }
            // One mistake often has Rhino report the tokens after it too.
            .filter { lines.add(lineOf(source, it.start)) }
    }

    /**
     * Rhino counts a character outside the Basic Multilingual Plane, as an emoji, as one position, and the editor as
     * two units: this gives the unit offset of a position of Rhino's.
     */
    private fun unitOffsets(source: String): (Int) -> Int {
        if (source.none(Char::isHighSurrogate))
            return { it.coerceIn(0, source.length) }
        // The unit offset of each position, as Rhino counts them.
        val offsets = IntArray(source.codePointCount(0, source.length) + 1)
        var unit = 0
        for (position in 0 until offsets.size - 1) {
            offsets[position] = unit
            unit += Character.charCount(source.codePointAt(unit))
        }
        offsets[offsets.size - 1] = source.length
        return { offsets[it.coerceIn(0, offsets.size - 1)] }
    }

    fun lineOf(source: String, offset: Int): Int {
        var line = 1
        for (i in 0 until minOf(offset, source.length))
            if (source[i] == '\n') line++
        return line
    }

    private fun lineStart(source: String, line: Int): Int {
        var current = 1
        var start = 0
        while (current < line) {
            val next = source.indexOf('\n', start)
            if (next < 0) return source.length
            start = next + 1
            current++
        }
        return start
    }
}
