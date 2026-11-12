/*
 * ScheduleSpec.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.schedule

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** When a scheduled task runs, in the device's time zone at the time of each run. */
internal sealed interface ScheduleSpec {

    /** As the task describes itself: `daily 09:00`, `cron 0 9 * * 1-5`, `once 2026-12-25T09:00:00Z`. */
    val description: String

    /** The first run, for a task made at [now]. */
    fun first(now: Instant, zone: ZoneId): Instant? =
        next(now, zone)

    /** The run after [after]; null when there is none. */
    fun next(after: Instant, zone: ZoneId): Instant?

    class Daily(val time: LocalTime) : ScheduleSpec {

        override val description: String
            get() = "daily ${time.format(if (time.second == 0) HOUR_MINUTE else HOUR_MINUTE_SECOND)}"

        override fun next(after: Instant, zone: ZoneId): Instant {
            val now = after.atZone(zone)
            // A time a clock change skips runs at the time after the gap, as ZonedDateTime resolves it.
            val today = ZonedDateTime.of(now.toLocalDate(), time, zone)
            return (if (today.toInstant().isAfter(after)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), time, zone))
                .toInstant()
        }

        companion object {
            private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")
            private val HOUR_MINUTE_SECOND = DateTimeFormatter.ofPattern("HH:mm:ss")
            private val INPUT = DateTimeFormatter.ofPattern("H:mm[:ss]")

            /** @throws IllegalArgumentException When [text] isn't `HH:mm` or `HH:mm:ss`. */
            fun parse(text: String): Daily =
                try {
                    Daily(LocalTime.parse(text.trim(), INPUT))
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("A daily time is 'HH:mm' or 'HH:mm:ss', as '09:00': '$text' isn't")
                }
        }
    }

    class Cron(val expression: CronExpression) : ScheduleSpec {

        override val description: String
            get() = "cron $expression"

        override fun next(after: Instant, zone: ZoneId): Instant? =
            expression.next(after.atZone(zone))?.toInstant()
    }

    /** Once at [at]; a time already past runs as soon as it can. */
    class Once(val at: Instant) : ScheduleSpec {

        override val description: String
            get() = "once $at"

        override fun first(now: Instant, zone: ZoneId): Instant =
            at

        override fun next(after: Instant, zone: ZoneId): Instant? =
            null
    }
}
