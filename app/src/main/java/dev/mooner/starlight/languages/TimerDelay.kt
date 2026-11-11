/*
 * TimerDelay.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages

/** How long `setTimeout` and `setInterval` wait, from what a script passed as the delay. */
internal object TimerDelay {

    /** The shortest wait between two runs of a repeating timer. */
    const val MIN_REPEAT_MILLIS = 1L

    /**
     * Milliseconds from a script's delay argument: a number, or text that reads as one as JavaScript takes it
     * (`"1000"`, which is what a value from `Env` is); anything else is 0.
     */
    fun fromScript(value: Any?): Long {
        val number = when (value) {
            is Number       -> value.toDouble()
            is CharSequence -> value.toString().trim().toDoubleOrNull()
            else            -> null
        }
        return number?.takeUnless(Double::isNaN)?.toLong() ?: 0L
    }

    /**
     * The wait a timer uses: never negative, and at least [MIN_REPEAT_MILLIS] when it repeats. A loop that never waits
     * never gives its thread back and can't be cancelled, so the project stops answering and a compile doesn't end it.
     */
    fun effective(millis: Long, repeat: Boolean): Long =
        millis.coerceAtLeast(if (repeat) MIN_REPEAT_MILLIS else 0L)
}
