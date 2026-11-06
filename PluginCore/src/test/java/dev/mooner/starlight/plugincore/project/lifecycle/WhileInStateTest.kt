package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle.State
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class WhileInStateTest {

    @Test
    fun waitsForTheStateAndCancelsTheBlockWhenItLeaves() = runBlocking {
        val state = MutableStateFlow(State.DISABLED)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val job = launch {
            state.whileIn(State.ENABLED) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        yield()
        assertFalse(started.isCompleted)

        state.value = State.ENABLED
        withTimeout(5.seconds) { started.await() }
        state.value = State.DISABLED
        withTimeout(5.seconds) { job.join() }

        assertTrue(cancelled.isCompleted)
        assertFalse(job.isCancelled)
    }

    @Test
    fun returnsWhenTheBlockFinishesInTheState() = runBlocking {
        val state = MutableStateFlow(State.ENABLED)
        var ran = false
        withTimeout(5.seconds) {
            state.whileIn(State.ENABLED) { ran = true }
        }
        assertTrue(ran)
        assertEquals(State.ENABLED, state.value)
    }

    @Test
    fun rethrowsBlockFailures() {
        val state = MutableStateFlow(State.ENABLED)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                state.whileIn(State.ENABLED) { error("boom") }
            }
        }
    }
}
