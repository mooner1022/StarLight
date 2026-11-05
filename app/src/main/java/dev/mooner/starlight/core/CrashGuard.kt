/*
 * CrashGuard.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stops the app from starting when a crash keeps coming back, without leaving the bots off after one that doesn't.
 *
 * The uncaught-exception handler [record]s every crash, along with whether the app was still starting. The next start
 * is blocked only when the last two starts failed the same way, or when a crash after starting came back
 * [RUNTIME_REPEATS] times within [RUNTIME_WINDOW]. Any other crash is reported once and the app starts as usual.
 * A start that ended without reaching the handler, such as a native crash or the system killing the process, also
 * counts as a failed start.
 */
internal object CrashGuard {

    /** What the last crashes mean for this start. */
    sealed interface Verdict {
        /** Nothing to tell. */
        data object Clear : Verdict
        /** The app crashed since the last start; tell the user, then start as usual. */
        data class Recovered(val crash: Crash) : Verdict
        /** The same crash keeps coming back; don't start until the user chooses to. */
        data class Blocked(val crash: Crash) : Verdict
        /** The user chose to start once without plugins and without compiling the projects. */
        data object SafeStart : Verdict
    }

    @Serializable
    data class Crash(
        val time: Long,
        val whileStarting: Boolean,
        /** What tells two crashes apart: the root exception's class and the app's own frame it was thrown from. */
        val signature: String,
        /** One line for notifications. */
        val summary: String,
        val report: String,
        val reported: Boolean = false,
    )

    @Serializable
    internal data class State(
        /** A start began and hasn't finished. */
        val starting: Boolean = false,
        /** How each start since the last finished one ended, oldest first. */
        val failedStarts: List<String> = emptyList(),
        /** The latest crashes, oldest first. */
        val crashes: List<Crash> = emptyList(),
        /** The crash that blocked starting, until the user continues. */
        val blocked: Crash? = null,
        /** The next start is to be a safe one; only that one. */
        val safeStart: Boolean = false,
    )

    /** A start that ended without an error report. */
    internal const val INTERRUPTED = "interrupted"

    internal const val STARTS_TO_BLOCK = 2
    internal const val RUNTIME_REPEATS = 3
    internal const val RUNTIME_WINDOW = 10 * 60 * 1000L

    private const val MAX_CRASHES = 5
    // The report also travels in intents, which have a size limit.
    private const val MAX_REPORT = 32_000

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.noBackupFilesDir, "crash_guard.json")
    }

    /**
     * Decides whether the app may start, from what the earlier processes left. [interruptedReport] describes a crash
     * that left no report.
     */
    @Synchronized
    fun onProcessStart(now: Long, interruptedReport: () -> String): Verdict {
        val (state, verdict) = decide(load(), now, interruptedReport)
        save(state)
        return verdict
    }

    /** Called when the app begins to start its runtime. */
    @Synchronized
    fun startBegan() =
        save(load().copy(starting = true))

    /** Called when the runtime started; failed starts before it no longer count. */
    @Synchronized
    fun startFinished() =
        save(load().copy(starting = false, failedStarts = emptyList()))

    /** Records a crash from the uncaught-exception handler; it runs on the crashing thread, so this writes at once. */
    @Synchronized
    fun record(throwable: Throwable, report: String, whileStarting: Boolean, now: Long = System.currentTimeMillis()) {
        val crash = Crash(
            time = now,
            whileStarting = whileStarting,
            signature = signatureOf(throwable),
            summary = summaryOf(throwable),
            report = report.take(MAX_REPORT),
        )
        save(recorded(load(), crash))
    }

    /** The user chose to start anyway, or with [safeStart] once: the crashes so far no longer count. */
    @Synchronized
    fun dismiss(safeStart: Boolean = false) =
        save(State(safeStart = safeStart))

    internal fun recorded(state: State, crash: Crash): State =
        state.copy(
            starting = false,
            failedStarts = if (crash.whileStarting) state.failedStarts + crash.signature else state.failedStarts,
            crashes = (state.crashes + crash).takeLast(MAX_CRASHES),
        )

    internal fun decide(state: State, now: Long, interruptedReport: () -> String): Pair<State, Verdict> {
        state.blocked?.let { return state to Verdict.Blocked(it) }
        if (state.safeStart)
            return state.copy(safeStart = false) to Verdict.SafeStart

        var current = state
        // The last process began to start and never got to finish or to report a crash.
        if (current.starting) {
            val crash = Crash(now, whileStarting = true, signature = INTERRUPTED, summary = INTERRUPTED,
                report = interruptedReport(), reported = true)
            current = current.copy(starting = false, failedStarts = current.failedStarts + INTERRUPTED,
                crashes = (current.crashes + crash).takeLast(MAX_CRASHES))
        }

        val latest = current.crashes.lastOrNull()
            ?: return current to Verdict.Clear

        val lastStarts = current.failedStarts.takeLast(STARTS_TO_BLOCK)
        val startsRepeat = lastStarts.size == STARTS_TO_BLOCK && lastStarts.distinct().size == 1
        val runtimeRepeats = !latest.whileStarting && current.crashes.count {
            !it.whileStarting && it.signature == latest.signature && latest.time - it.time < RUNTIME_WINDOW
        } >= RUNTIME_REPEATS
        if (startsRepeat || runtimeRepeats) {
            val crash = latest.copy(reported = true)
            return current.copy(blocked = crash, crashes = current.crashes.dropLast(1) + crash) to Verdict.Blocked(crash)
        }

        val unreported = current.crashes.indexOfLast { !it.reported }
        if (unreported < 0)
            return current to Verdict.Clear
        val crash = current.crashes[unreported].copy(reported = true)
        val crashes = current.crashes.toMutableList().also { it[unreported] = crash }
        return current.copy(crashes = crashes) to Verdict.Recovered(crash)
    }

    internal fun signatureOf(throwable: Throwable): String {
        val root = rootOf(throwable)
        val frame = root.stackTrace.firstOrNull { it.className.startsWith("dev.mooner.") }
            ?: root.stackTrace.firstOrNull()
        return root.javaClass.name + (frame?.let { " at ${it.className}.${it.methodName}" } ?: "")
    }

    private fun summaryOf(throwable: Throwable): String {
        val root = rootOf(throwable)
        return root.javaClass.simpleName + (root.message?.let { ": ${it.lineSequence().first()}" } ?: "")
    }

    private fun rootOf(throwable: Throwable): Throwable =
        generateSequence(throwable) { it.cause?.takeIf { cause -> cause !== it } }.take(32).last()

    private fun load(): State =
        try {
            if (file.isFile) json.decodeFromString(file.readText()) else State()
        } catch (e: Exception) {
            State()
        }

    private fun save(state: State) {
        try {
            val temp = File(file.path + ".tmp")
            temp.writeText(json.encodeToString(State.serializer(), state))
            if (!temp.renameTo(file))
                file.writeText(temp.readText())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
