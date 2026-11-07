package dev.mooner.starlight.languages.neonjs

import android.os.Looper
import dev.mooner.neonjs.NeonTimeoutException

/**
 * Bounds how long the main thread waits for a NeonJS script's context that another thread is using, only where the
 * caller handles the [NeonTimeoutException] that ends the wait (`App.runOnUiThread`, `Api.UIThread`). Elsewhere, as in
 * a Java callback a script gave Android (a `Runnable` posted to the main thread, a view's listener), nothing would catch
 * it and the app would close, so those wait as long as it takes.
 */
object MainThreadWait {

    /** Well before Android's 5-second limit for answering input. */
    const val LIMIT_MILLIS = 2_000L

    /** Set while the main thread runs a [bounded] block whose call hasn't entered its context yet. */
    private val armed = ThreadLocal<Boolean>()

    /**
     * Runs [block] with its first wait for a context bounded by [LIMIT_MILLIS]: past it, entering the script throws
     * [NeonTimeoutException] instead of freezing the app. Only the first: in a main-thread message of its own, which
     * holds no context yet, that is the wait to start the call, so a timeout there means the call didn't start; what the
     * script then calls in other contexts waits as before.
     */
    fun <R> bounded(block: () -> R): R {
        val previous = armed.get()
        armed.set(true)
        try {
            return block()
        } finally {
            if (previous == null) armed.remove() else armed.set(previous)
        }
    }

    /** Whether the wait [thread] is about to make for a context is bounded; asked by NeonJS on that thread. */
    fun appliesTo(thread: Thread): Boolean {
        if (thread !== Looper.getMainLooper().thread || armed.get() != true)
            return false
        armed.set(false)
        return true
    }
}
