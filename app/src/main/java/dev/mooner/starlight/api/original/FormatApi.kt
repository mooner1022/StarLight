/*
 * FormatApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.script.ScriptTimes
import dev.mooner.starlight.script.format.Formats
import dev.mooner.starlight.script.format.Josa
import java.time.Instant

class FormatApi: Api<FormatApi.Format>() {

    /**
     * Numbers, dates, durations and Korean particles as text; a StarLight original global. It works the same in both
     * engines, and stands in for `Intl` and `toLocaleString()`, which NeonJS lacks.
     *
     * ```js
     * Format.number(1234567.5);                      // "1,234,567.5"
     * Format.date(new Date(), "M월 d일 (E) HH:mm");  // "11월 12일 (목) 09:30"
     * Format.duration(3725000);                      // "1시간 2분 5초"
     * Format.josa(event.sender.name, "이/가");        // "철수가", "영철이"
     * ```
     */
    class Format internal constructor() {

        /**
         * [value] with its whole part grouped by threes, as `1,234,567.5`, with all of the fraction the number has:
         * `5` for 5, `0.30000000000000004` for `0.1 + 0.2`. `,` and `.` are used on every device.
         *
         * @param value A number or a BigInt; `NaN` and infinities come back as `NaN`, `Infinity` and `-Infinity`.
         * @throws IllegalArgumentException When [value] isn't a number.
         */
        fun number(value: Any?): String =
            Formats.number(numberOf(value, "number"))

        /**
         * [value] with its whole part grouped by threes and exactly [fractionDigits] digits after the point, rounded
         * half up: `Format.number(1234.5, 2)` gives `1,234.50`.
         *
         * @param value A number or a BigInt.
         * @param fractionDigits From 0 to 20.
         * @throws IllegalArgumentException When [value] isn't a number, or [fractionDigits] is out of range.
         */
        fun number(value: Any?, fractionDigits: Int): String =
            Formats.number(numberOf(value, "number"), fractionDigits)

        /**
         * [time] written as [pattern] says, in the device's time zone, with month and day names in the device's
         * language.
         *
         * @param time A `Date`, milliseconds since 1970 as `Date.now()` gives them, or ISO 8601 text: `"2026-12-25T09:00"`
         * is in the device's time zone, `"2026-12-25T09:00+09:00"` and `"2026-12-25T00:00Z"` name theirs.
         * @param pattern A Java `DateTimeFormatter` pattern: `yyyy` year, `M`/`MM` month, `d`/`dd` day, `E` day of
         * the week, `HH` hour (0–23), `h` hour (1–12) with `a` for AM/PM, `mm` minute, `ss` second. Text in quotes,
         * as `'at'`, is written as it is; Korean letters may stand without quotes, as in `"M월 d일"`.
         * @throws IllegalArgumentException When [time] is none of these, or [pattern] isn't one.
         */
        fun date(time: Any?, pattern: String): String =
            Formats.date(instantOf(time), pattern)

        /**
         * [time] written as [pattern] says, in the time zone [zone].
         *
         * @param time A `Date`, milliseconds since 1970 as `Date.now()` gives them, or ISO 8601 text; text without a
         * zone, as `"2026-12-25T09:00"`, is read in the device's time zone, not in [zone].
         * @param pattern A Java `DateTimeFormatter` pattern, as for the form without a zone.
         * @param zone A time zone ID, as `"Asia/Seoul"` or `"UTC"`.
         * @throws IllegalArgumentException When [time] is none of these, or [pattern] or [zone] isn't one.
         */
        fun date(time: Any?, pattern: String, zone: String): String =
            Formats.date(instantOf(time), pattern, zone)

        /**
         * [millis] as days, hours, minutes and seconds, leaving out those that are 0: `1시간 2분 5초` when the device's
         * language is Korean, `1h 2m 5s` otherwise; `0초` or `0s` under a second. The fraction of a second is
         * dropped, and a negative duration starts with `-`.
         *
         * @param millis A number of milliseconds.
         * @throws IllegalArgumentException When [millis] isn't a number, or is `NaN` or infinite.
         */
        fun duration(millis: Any?): String =
            Formats.duration(numberOf(millis, "duration").toDouble())

        /**
         * [word] followed by the form of the Korean particle [pair] that fits it: `Format.josa("사과", "을/를")` gives
         * `사과를`, `Format.josa("책", "을/를")` gives `책을`.
         *
         * - The forms may come in either order, and with 이 or 으 before the other, as `이랑/랑`, `으로/로` and
         *   `이나/나`. With other pairs, the first is the one that follows a final consonant (받침).
         * - Spaces, closing brackets and quotes, and `.`, `,`, `!`, `?`, `~`, `…` at the end of [word] are passed
         *   over in finding its last sound, and the particle still follows them: `"사과"` gives `"사과"를`. A word
         *   ending in ㄹ takes `로`, as in `서울로`.
         * - Digits are read in Korean (`3이`, `2가`, `10은`). English words are guessed from their last letters, as
         *   their sound can't be told from the spelling. Other characters count as ending in a vowel.
         *
         * @param word The word, as a name.
         * @param pair Two forms split by `/`, as `"을/를"`, `"이/가"`, `"은/는"`, `"과/와"`, `"아/야"` or `"으로/로"`.
         * @throws IllegalArgumentException When [pair] isn't two forms split by `/`.
         */
        fun josa(word: String, pair: String): String =
            Josa.attach(word, pair)

        private fun numberOf(value: Any?, function: String): Number =
            ScriptValues.unwrap(value) as? Number
                ?: throw IllegalArgumentException("Format.$function takes a number")

        private fun instantOf(time: Any?): Instant =
            ScriptTimes.instantOf(time) ?: throw IllegalArgumentException(
                "Format.date takes a Date, milliseconds since 1970 or ISO 8601 text, as \"2026-12-25T09:00\"")

        override fun toString(): String =
            "Format"
    }

    override val name: String = "Format"

    override val objects: List<ApiObject> =
        getApiObjects<Format>()

    override val instanceClass: Class<Format> =
        Format::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Format()
}
