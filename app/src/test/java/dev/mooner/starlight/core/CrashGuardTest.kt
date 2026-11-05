package dev.mooner.starlight.core

import dev.mooner.starlight.core.CrashGuard.Crash
import dev.mooner.starlight.core.CrashGuard.State
import dev.mooner.starlight.core.CrashGuard.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a crash only gets reported, and when it keeps the app from starting. */
class CrashGuardTest {

    private val minute = 60_000L

    private fun crash(signature: String, time: Long, whileStarting: Boolean) =
        Crash(time, whileStarting, signature, summary = signature, report = "report of $signature")

    private fun decide(state: State, now: Long = 0L) =
        CrashGuard.decide(state, now) { "interrupted report" }

    /** The state after the process crashed the way [crash] says. */
    private fun State.after(crash: Crash) =
        CrashGuard.recorded(this, crash)

    /** The state after a process started and decided, then began to start its runtime. */
    private fun State.started(now: Long = 0L): State =
        decide(this, now).first.copy(starting = true)

    @Test
    fun `nothing to tell without crashes`() {
        assertEquals(Verdict.Clear, decide(State()).second)
    }

    @Test
    fun `a crash after starting is reported once`() {
        val state = State().started().after(crash("A", time = 0, whileStarting = false))

        val (next, verdict) = decide(state)
        assertTrue(verdict is Verdict.Recovered)
        assertEquals("A", (verdict as Verdict.Recovered).crash.signature)
        assertEquals(Verdict.Clear, decide(next).second)
    }

    @Test
    fun `one crash while starting doesn't block`() {
        val state = State().started().after(crash("A", time = 0, whileStarting = true))

        assertTrue(decide(state).second is Verdict.Recovered)
    }

    @Test
    fun `the same crash on two starts in a row blocks until dismissed`() {
        val state = State().started()
            .after(crash("A", time = 0, whileStarting = true)).started()
            .after(crash("A", time = minute, whileStarting = true))

        val (blocked, verdict) = decide(state)
        assertTrue(verdict is Verdict.Blocked)
        assertEquals("report of A", (verdict as Verdict.Blocked).crash.report)
        // It stays blocked, also after crashes the blocked start couldn't have had.
        assertTrue(decide(blocked).second is Verdict.Blocked)
    }

    @Test
    fun `different crashes on two starts don't block`() {
        val state = State().started()
            .after(crash("A", time = 0, whileStarting = true)).started()
            .after(crash("B", time = minute, whileStarting = true))

        assertTrue(decide(state).second is Verdict.Recovered)
    }

    @Test
    fun `a start that finished resets the count`() {
        val state = State().started()
            .after(crash("A", time = 0, whileStarting = true)).started()
            // The runtime started this time, then crashed later.
            .copy(starting = false, failedStarts = emptyList())
            .after(crash("B", time = minute, whileStarting = false)).started()
            .after(crash("A", time = 2 * minute, whileStarting = true))

        assertTrue(decide(state).second is Verdict.Recovered)
    }

    @Test
    fun `a crash after starting blocks when it comes back three times within ten minutes`() {
        var state = State()
        for (i in 0 until CrashGuard.RUNTIME_REPEATS)
            state = state.started().copy(starting = false).after(crash("A", time = i * 4 * minute, whileStarting = false))

        assertTrue(decide(state).second is Verdict.Blocked)
    }

    @Test
    fun `a crash after starting that comes back slowly is only reported`() {
        var state = State()
        for (i in 0 until CrashGuard.RUNTIME_REPEATS)
            state = state.started().copy(starting = false).after(crash("A", time = i * 6 * minute, whileStarting = false))

        assertTrue(decide(state).second is Verdict.Recovered)
    }

    @Test
    fun `a start that ended without a report counts as failed`() {
        // Once: nothing to report, since the process may just have been stopped.
        val once = State().started()
        val (afterOnce, first) = decide(once)
        assertEquals(Verdict.Clear, first)

        // Twice in a row: blocked, with the report written for it.
        val (_, second) = decide(afterOnce.copy(starting = true))
        assertTrue(second is Verdict.Blocked)
        assertEquals("interrupted report", (second as Verdict.Blocked).crash.report)
    }

    @Test
    fun `a safe start is used once`() {
        val (next, verdict) = decide(State(safeStart = true))
        assertEquals(Verdict.SafeStart, verdict)
        assertEquals(Verdict.Clear, decide(next).second)
    }

    @Test
    fun `the signature is the root cause and the app frame it came from`() {
        val root = IllegalArgumentException("Unable to find project with id: 1234")
        root.stackTrace = arrayOf(
            StackTraceElement("kotlin.collections.CollectionsKt", "first", "Collections.kt", 1),
            StackTraceElement("dev.mooner.starlight.ui.editor.drawer.EditorLogsFragment", "onCreate", "EditorLogsFragment.kt", 53),
        )
        val wrapped = RuntimeException("Unable to start activity", root)

        assertEquals("java.lang.IllegalArgumentException at dev.mooner.starlight.ui.editor.drawer.EditorLogsFragment.onCreate",
            CrashGuard.signatureOf(wrapped))
        // Messages that differ between crashes don't make them different.
        val other = RuntimeException("Unable to start activity", IllegalArgumentException("Unable to find project with id: 5678")
            .also { it.stackTrace = root.stackTrace })
        assertEquals(CrashGuard.signatureOf(wrapped), CrashGuard.signatureOf(other))
    }
}
