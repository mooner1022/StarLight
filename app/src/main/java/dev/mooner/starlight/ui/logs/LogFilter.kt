/*
 * LogFilter.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.logs

import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import java.util.regex.Pattern

/**
 * Which logs the Logs tab shows: the ones of [types], with a tag that has one of [tags] in it, whose message has
 * a match of [message] in it. A condition left empty lets every log through.
 *
 * A word typed finds the logs it is in, whatever their case. Tags used to be taken only when equal to one typed,
 * and messages only when the pattern covered them from their first character to their last, so that typing
 * `error` showed nothing.
 */
internal class LogFilter(
    val types: List<LogType> = emptyList(),
    val tags: List<String> = emptyList(),
    val message: Pattern? = null,
) {

    /** How many of the three conditions are set. */
    val conditions: Int
        get() = listOf(types.isNotEmpty(), tags.isNotEmpty(), message != null).count { it }

    val isActive: Boolean
        get() = conditions > 0

    fun matches(log: LogData): Boolean =
        matches(log.type, log.tag, log.message)

    fun matches(type: LogType, tag: String?, text: String): Boolean =
        (types.isEmpty() || type in types) &&
            (tags.isEmpty() || tags.any { tag.orEmpty().contains(it, ignoreCase = true) }) &&
            (message == null || message.matcher(text).find())

    companion object {

        /**
         * The pattern for what the user typed as the message condition; throws
         * [java.util.regex.PatternSyntaxException] for one that isn't a regular expression.
         */
        fun messagePattern(text: String): Pattern =
            Pattern.compile(text, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }
}
