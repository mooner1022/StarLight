package dev.mooner.starlight.plugincore.event

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class EventHandlerTest {

    private class CancelEvent: Event, CoroutineScope by eventHandlerScope()

    private class ThreadEvent: Event, CoroutineScope by eventHandlerScope()

    private suspend fun fireUntil(done: CompletableDeferred<*>, event: () -> Event) =
        withTimeout(5.seconds) {
            while (!done.isCompleted) {
                EventHandler.fireEvent(event())
                delay(10)
            }
        }

    @Test
    fun cancelsRunningHandlersWithTheSubscriberScope() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        EventHandler.on<CancelEvent>(scope) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        fireUntil(started, ::CancelEvent)
        scope.cancel()
        withTimeout(5.seconds) { cancelled.await() }
    }

    @Test
    fun runsHandlersOnTheEventDispatcher() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val thread = CompletableDeferred<String>()
        EventHandler.on<ThreadEvent>(scope) {
            thread.complete(Thread.currentThread().name)
        }
        fireUntil(thread, ::ThreadEvent)
        assertTrue(thread.await(), thread.await().startsWith("DefaultDispatcher"))
        scope.cancel()
    }
}
