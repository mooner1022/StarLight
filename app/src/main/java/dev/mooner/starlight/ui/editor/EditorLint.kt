/*
 * EditorLint.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor

import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.script.lint.ScriptLint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = LoggerFactory.logger {  }

/**
 * Checks the editor page's scripts with the app's own linters ([ScriptLint]) instead of the page's: the page asks
 * whether there is one for what it shows, sends the text, and is told the findings.
 *
 * The page's calls come on its bridge's thread, the check runs on a thread of this, and [reply] is called from that
 * thread. A check of a tab is given up when the tab asks again, as its text has changed.
 *
 * @param reply Tells the page the outcome of request [requestId]: the findings as a JSON array, or null when the
 * check couldn't be made, for which the page checks the text itself.
 */
internal class EditorLint(
    private val reply: (requestId: Int, json: String?) -> Unit,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        // A parser goes as deep as the code is nested.
        Thread(null, task, "editor-lint", STACK_BYTES).apply { isDaemon = true }
    },
) {

    /** The project the editor is open on; null for a file of none. */
    @Volatile
    var project: Project? = null
        private set

    /** The language of the project's scripts, which says which linter checks them; null for a file of no project. */
    @Volatile
    var languageId: String? = null

    private val running: MutableMap<String, AtomicBoolean> = ConcurrentHashMap()

    /** The screen says what the editor is open on. */
    fun open(project: Project?) {
        this.project = project
        languageId = project?.info?.languageId
    }

    private fun linter(): ScriptLint.Linter? =
        ScriptLint.linterFor(languageId)

    /** Whether the scripts of the project the editor is open on are checked here. */
    fun handles(): Boolean =
        linter() != null

    /** Checks [code], the text of the tab [sessionId], and replies to [requestId]. */
    fun request(requestId: Int, sessionId: String, code: String) {
        val linter = linter()
            ?: return reply(requestId, null)
        val cancelled = AtomicBoolean()
        // The tab's text has changed: its last check is of what is gone.
        running.put(sessionId, cancelled)?.set(true)
        val project = project
        try {
            executor.execute {
                val json = if (cancelled.get()) null else try {
                    encode(linter.lint(ScriptLint.Request(code, project, cancelled::get)), code.length)
                } catch (e: Throwable) {
                    // Whatever a linter does on text that is half typed, the editor goes on: the page checks instead.
                    runCatching { LOG.warn { "The linter failed, so the editor's own checks this text: $e" } }
                    null
                }
                running.remove(sessionId, cancelled)
                reply(requestId, json)
            }
        } catch (_: RejectedExecutionException) {
            // Closed meanwhile.
            reply(requestId, null)
        }
    }

    /** Ends the checks: the editor is closed. */
    fun close() {
        running.values.forEach { it.set(true) }
        executor.shutdownNow()
    }

    companion object {
        private const val STACK_BYTES = 4L * 1024 * 1024

        /** The findings as the page reads them, each within the [length] of the text. */
        fun encode(diagnostics: List<ScriptLint.Diagnostic>, length: Int): String =
            JsonArray(diagnostics.map { diagnostic ->
                val start = diagnostic.start.coerceIn(0, length)
                JsonObject(buildMap {
                    put("start", JsonPrimitive(start))
                    put("end", JsonPrimitive(diagnostic.end.coerceIn(start, length)))
                    put("severity", JsonPrimitive(if (diagnostic.severity == ScriptLint.Severity.ERROR) "error" else "warning"))
                    put("message", JsonPrimitive(diagnostic.message))
                    diagnostic.code?.let { put("code", JsonPrimitive(it)) }
                })
            }).toString()
                // JSON leaves these two as they are, and an older WebView reads them as the end of a line of script.
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029")
    }
}
