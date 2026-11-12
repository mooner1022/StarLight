/*
 * ScriptTimes.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script

import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.media.script.ScriptValues
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Date

/** Times as scripts give them, for the APIs that take one: `Schedule.once`, `Schedule.at` and `Format.date`. */
internal object ScriptTimes {

    /**
     * A script's time value as an instant: a JS Date, milliseconds since 1970, a java.util.Date, an Instant, or ISO 8601
     * text. Null when it is none of these, or a number or Date that names no time (`NaN`, `new Date("x")`).
     */
    fun instantOf(time: Any?): Instant? {
        val value = ScriptValues.unwrap(time)
        return when {
            value is NeonValue && value.isObject -> runCatching { value.asInstant() }.getOrNull()
            value is Scriptable && value.className == "Date" -> ofMillis(Context.toNumber(value))
            value is Number       -> ofMillis(value.toDouble())
            value is Date         -> value.toInstant()
            value is Instant      -> value
            value is CharSequence -> parseTime(value.toString())
            else                  -> null
        }
    }

    /** NaN and the infinities would otherwise read as 1970 or the ends of time. */
    private fun ofMillis(millis: Double): Instant? =
        if (millis.isFinite()) Instant.ofEpochMilli(millis.toLong()) else null

    private fun parseTime(text: String): Instant? =
        try {
            Instant.parse(text)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(text).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
}
