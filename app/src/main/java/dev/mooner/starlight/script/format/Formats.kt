/*
 * Formats.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.format

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Numbers, dates and durations as text, for `Format`; the same on every device but for the names of days and units. */
internal object Formats {

    var locale: () -> Locale = Locale::getDefault
    var zone: () -> ZoneId = ZoneId::systemDefault

    private const val MAX_FRACTION_DIGITS = 20

    /**
     * [value] with its whole part grouped by threes with `,` and `.` before its fraction: all of the fraction a
     * double shows, or [fractionDigits] digits rounded half up.
     *
     * @throws IllegalArgumentException When [fractionDigits] isn't from 0 to 20.
     */
    fun number(value: Number, fractionDigits: Int? = null): String {
        require(fractionDigits == null || fractionDigits in 0..MAX_FRACTION_DIGITS) {
            "Format.number takes 0 to $MAX_FRACTION_DIGITS fraction digits: $fractionDigits isn't"
        }
        if (value is Double && !value.isFinite() || value is Float && !value.isFinite())
            return value.toDouble().toString()
        val decimal = when (value) {
            is BigDecimal -> value
            is BigInteger -> BigDecimal(value)
            is Double     -> BigDecimal.valueOf(value)
            is Float      -> BigDecimal(value.toString())
            else          -> BigDecimal.valueOf(value.toLong())
        }
        val shown = if (fractionDigits == null)
            decimal.stripTrailingZeros()
        else
            decimal.setScale(fractionDigits, RoundingMode.HALF_UP)
        val plain = shown.abs().toPlainString()
        val point = plain.indexOf('.')
        val whole = if (point < 0) plain else plain.substring(0, point)
        val fraction = if (point < 0) "" else plain.substring(point)
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        return (if (shown.signum() < 0) "-" else "") + grouped + fraction
    }

    /**
     * [time] as [pattern] writes it, a `DateTimeFormatter` pattern, in the time zone [zoneId] or the device's, with the
     * names of months and days in the device's language.
     *
     * @throws IllegalArgumentException When [pattern] or [zoneId] isn't one.
     */
    fun date(time: Instant, pattern: String, zoneId: String? = null): String {
        val formatter = try {
            DateTimeFormatter.ofPattern(pattern, locale())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Format.date: '$pattern' isn't a date pattern: ${e.message}", e)
        }
        val zone = try {
            zoneId?.let(ZoneId::of) ?: zone()
        } catch (e: DateTimeException) {
            throw IllegalArgumentException("Format.date: '$zoneId' isn't a time zone: ${e.message}", e)
        }
        return try {
            formatter.format(time.atZone(zone))
        } catch (e: DateTimeException) {
            throw IllegalArgumentException("Format.date: '$pattern' can't write a date: ${e.message}", e)
        }
    }

    /**
     * [millis] as days, hours, minutes and seconds, leaving out those that are 0: `1시간 2분 5초` when the device's
     * language is Korean, `1h 2m 5s` otherwise. The fraction of a second is dropped.
     *
     * @throws IllegalArgumentException When [millis] is NaN or infinite.
     */
    fun duration(millis: Double): String {
        require(millis.isFinite()) { "Format.duration takes a number of milliseconds: $millis isn't" }
        val units = if (locale().language == Locale.KOREAN.language) KOREAN_UNITS else UNITS
        var seconds = (Math.abs(millis) / 1000).toLong()
        if (seconds == 0L)
            return "0${units.last()}"
        val parts = mutableListOf<String>()
        for ((index, size) in UNIT_SECONDS.withIndex()) {
            val count = seconds / size
            seconds %= size
            if (count > 0)
                parts += "$count${units[index]}"
        }
        return (if (millis < 0) "-" else "") + parts.joinToString(" ")
    }

    private val UNIT_SECONDS = longArrayOf(86_400, 3_600, 60, 1)
    private val UNITS = listOf("d", "h", "m", "s")
    private val KOREAN_UNITS = listOf("일", "시간", "분", "초")
}
