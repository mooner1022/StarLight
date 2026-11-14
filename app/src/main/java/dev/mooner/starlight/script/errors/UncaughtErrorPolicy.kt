/*
 * UncaughtErrorPolicy.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.errors

/**
 * How often one compiled version's `onUncaughtError` is called: at most [MAX_CALLS] times in [WINDOW_MILLIS], and not
 * again after it failed [MAX_FAILURES] times in a row. Errors in a handler's own timers, or a flood of them in a chat
 * room, stop there.
 */
internal class UncaughtErrorPolicy(private val clock: () -> Long) {

    enum class Decision {
        /** Call the handler. */
        CALL,
        /** Don't: the limit was just reached, which is worth telling once. */
        LIMITED_FIRST,
        /** Don't: the limit still holds. */
        LIMITED,
        /** Don't: the handler failed too often. */
        DISABLED,
    }

    /** When the calls in the window were made, oldest first. */
    private val calls = ArrayDeque<Long>()

    private var failures = 0

    private var disabled = false

    /** Whether [Decision.LIMITED_FIRST] was given since the last call. */
    private var limitTold = false

    @Synchronized
    fun next(): Decision {
        if (disabled)
            return Decision.DISABLED
        val now = clock()
        while (calls.isNotEmpty() && now - calls.first() >= WINDOW_MILLIS)
            calls.removeFirst()
        if (calls.size >= MAX_CALLS) {
            if (limitTold)
                return Decision.LIMITED
            limitTold = true
            return Decision.LIMITED_FIRST
        }
        limitTold = false
        calls.addLast(now)
        return Decision.CALL
    }

    @Synchronized
    fun succeeded() {
        failures = 0
    }

    /** Counts a failure; true when it is the one that disables the handler. */
    @Synchronized
    fun failed(): Boolean {
        if (disabled)
            return false
        failures++
        if (failures < MAX_FAILURES)
            return false
        disabled = true
        return true
    }

    companion object {
        const val WINDOW_MILLIS = 60_000L
        const val MAX_CALLS = 10
        const val MAX_FAILURES = 3
    }
}
