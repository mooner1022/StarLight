/*
 * CronExpression.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.schedule

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.BitSet

/**
 * A five-field cron expression, `minute hour day-of-month month day-of-week`, as crontab reads it.
 *
 * Each field is `*`, a number, a range `a-b`, a step `*&#47;n`, `a-b/n` or `a/n` (from `a` to the end), or a list of these
 * separated by commas. Months may be `JAN`…`DEC` and days of the week `SUN`…`SAT`, in any case; Sunday is 0 or 7.
 * When both day fields are restricted, a day matching either runs, as in crontab.
 */
internal class CronExpression private constructor(
    val text: String,
    private val minutes: BitSet,
    private val hours: BitSet,
    private val days: BitSet,
    private val months: BitSet,
    private val weekdays: BitSet,
    private val anyDay: Boolean,
    private val anyWeekday: Boolean,
) {

    /** The first time after [after], to the minute, in [after]'s zone; null when there is none in five years. */
    fun next(after: ZonedDateTime): ZonedDateTime? {
        var time = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val limit = after.plusYears(5)
        while (time.isBefore(limit)) {
            when {
                !months[time.monthValue] ->
                    time = time.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(1)
                !dayMatches(time) ->
                    time = time.truncatedTo(ChronoUnit.DAYS).plusDays(1)
                !hours[time.hour] ->
                    time = time.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                !minutes[time.minute] ->
                    time = time.plusMinutes(1)
                else ->
                    return time
            }
        }
        return null
    }

    private fun dayMatches(time: ZonedDateTime): Boolean {
        val day = days[time.dayOfMonth]
        val weekday = weekdays[time.dayOfWeek.value % 7]
        return when {
            anyDay && anyWeekday -> true
            anyDay               -> weekday
            anyWeekday           -> day
            else                 -> day || weekday
        }
    }

    override fun toString(): String =
        text

    companion object {

        private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private val WEEKDAYS = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        /** @throws IllegalArgumentException When [text] isn't one, saying what is wrong. */
        fun parse(text: String): CronExpression {
            val fields = text.trim().split(Regex("\\s+"))
            require(fields.size == 5) {
                "A cron expression has five fields, 'minute hour day month weekday', as '0 9 * * 1-5': '$text' has ${fields.size}"
            }
            val weekdays = field(fields[4], "day of the week", 0, 7, WEEKDAYS, 0)
            if (weekdays[7])
                weekdays.set(0)
            return CronExpression(
                text = fields.joinToString(" "),
                minutes = field(fields[0], "minute", 0, 59),
                hours = field(fields[1], "hour", 0, 23),
                days = field(fields[2], "day of the month", 1, 31),
                months = field(fields[3], "month", 1, 12, MONTHS, 1),
                weekdays = weekdays,
                anyDay = fields[2] == "*",
                anyWeekday = fields[4] == "*",
            )
        }

        private fun field(text: String, what: String, min: Int, max: Int, names: List<String> = emptyList(), firstName: Int = 0): BitSet {
            val bits = BitSet(max + 1)
            fun number(value: String): Int {
                val index = names.indexOf(value.uppercase())
                val n = if (index >= 0) index + firstName else value.toIntOrNull()
                require(n != null && n in min..max) { "The $what in a cron expression is from $min to $max: '$value' isn't" }
                return n
            }
            for (part in text.split(',')) {
                require(part.isNotEmpty()) { "The $what in a cron expression has an empty item: '$text'" }
                val (range, step) = part.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
                val by = step?.let {
                    val n = it.toIntOrNull()
                    require(n != null && n > 0) { "A step in a cron expression is a whole number from 1: '$part' isn't" }
                    n
                } ?: 1
                val (from, to) = when {
                    range == "*"        -> min to max
                    '-' in range        -> range.split('-', limit = 2).let { (a, b) -> number(a) to number(b) }
                    step != null        -> number(range) to max
                    else                -> number(range).let { it to it }
                }
                require(from <= to) { "A range in a cron expression goes up: '$part' doesn't" }
                for (n in from..to step by)
                    bits.set(n)
            }
            return bits
        }
    }
}
