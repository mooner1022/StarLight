/*
 * ScriptLint.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.lint

import dev.mooner.starlight.plugincore.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks of a script's text before it runs, as the editor shows while typing: what a linter finds, in the app's terms.
 * A linter is registered for a language's ID; the scripts of a language with none are checked by [default].
 *
 * The types here are the app's own, so that what uses the results never sees a linter library's: only the linter
 * registered for a language knows the library behind it.
 */
object ScriptLint {

    enum class Severity { ERROR, WARNING }

    /**
     * One finding.
     *
     * @param start Where it begins in the text that was checked, as an offset in UTF-16 units.
     * @param end Where it ends, exclusive; [start] for a point.
     * @param message What to show, in the app's language where the linter has it.
     * @param code The linter's stable name for this kind of finding, when it has one.
     * @param args What [message] was filled in with, for a translation by [code].
     */
    class Diagnostic(
        val start: Int,
        val end: Int,
        val severity: Severity,
        val message: String,
        val code: String? = null,
        val args: List<String> = emptyList(),
    )

    /**
     * @param source The text to check: what the editor holds, which may not be saved.
     * @param project The project the file is of, for what its scripts are given; null for a file of none.
     * @param isCancelled Whether the result is no longer wanted, as the text changed: a linter may stop early.
     */
    class Request(
        val source: String,
        val project: Project?,
        val isCancelled: () -> Boolean = { false },
    )

    /** A language's linter. Called on a background thread, for one text at a time; it may throw. */
    fun interface Linter {
        fun lint(request: Request): List<Diagnostic>
    }

    private val linters: MutableMap<String, Linter> = ConcurrentHashMap()

    /** Makes [linter] the one for scripts of the language [languageId], in place of an earlier one. */
    fun register(languageId: String, linter: Linter) {
        linters[languageId] = linter
    }

    fun unregister(languageId: String) {
        linters -= languageId
    }

    /** The linter for the scripts of a language that has none of its own, and of a file of no project. */
    @Volatile
    var default: Linter? = null

    /** The linter for the language [languageId]: its own, or [default]. */
    fun linterFor(languageId: String?): Linter? =
        languageId?.let(linters::get) ?: default
}
