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

    /** A script's time value as an instant: a JS Date, milliseconds since 1970, a java.util.Date, an Instant, or ISO 8601 text. Null when it is none of these. */
    fun instantOf(time: Any?): Instant? {
        val value = ScriptValues.unwrap(time)
        return when {
            value is NeonValue && value.isObject -> runCatching { value.asInstant() }.getOrNull()
            value is Scriptable && value.className == "Date" -> Instant.ofEpochMilli(Context.toNumber(value).toLong())
            value is Number       -> Instant.ofEpochMilli(value.toLong())
            value is Date         -> value.toInstant()
            value is Instant      -> value
            value is CharSequence -> parseTime(value.toString())
            else                  -> null
        }
    }

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
