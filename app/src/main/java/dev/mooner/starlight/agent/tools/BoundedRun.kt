package dev.mooner.starlight.agent.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs blocking work that may never end, such as a user's script, on a thread of its own, and waits for it a bounded
 * time. The waiting coroutine can be cancelled; the work can't be stopped, and goes on when the wait ends.
 */
internal object BoundedRun {

    /** How [run] ended: [finished] in time, with [error] if the work threw; on [thread], which may still be running. */
    class Outcome(val thread: Thread, val finished: Boolean, val error: Throwable?)

    /**
     * Starts [work] on a new thread named [name] and waits at most [millis] for it. [canStart] gets the thread before it
     * starts, to register it: returning false leaves it unstarted and gives null. [onEnd] is called on that thread once
     * the work ends, however late.
     */
    suspend fun run(
        name: String,
        millis: Long,
        canStart: (Thread) -> Boolean = { true },
        onEnd: (Thread) -> Unit = {},
        work: () -> Unit,
    ): Outcome? {
        val done = CompletableDeferred<Throwable?>()
        val thread = Thread({
            try {
                done.complete(runCatching(work).exceptionOrNull())
            } finally {
                onEnd(Thread.currentThread())
            }
        }, name)
        if (!canStart(thread))
            return null
        thread.start()
        val ended = withTimeoutOrNull(millis) { done.await() ?: NO_ERROR }
        return Outcome(thread, finished = ended != null, error = ended?.takeUnless { it === NO_ERROR })
    }

    /** What a wait that ended without an error yields: null is the timeout's. */
    private val NO_ERROR = Throwable()
}
