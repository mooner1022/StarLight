/*
 * TextOptions.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.media.script

import dev.mooner.starlight.media.send.KakaoTextAttachment
import dev.mooner.starlight.media.send.LeaveTo

/**
 * The options of a text sent through KakaoTalk's share screen: `{ markdown, spoilers, emoticons, leave }`.
 *
 * - `markdown`: `true` to show the text as Markdown.
 * - `spoilers`: ranges hidden until tapped, each `{ start, length }` (or `[start, length]`), counted in characters
 *   from 0. Not with `markdown`: KakaoTalk shows no spoilers in a Markdown message.
 * - `emoticons`: mini emoticons, each its ID (`"<item code>_<number>"`), placed on the next placeholder, or
 *   `{ id, at, length }`, placed on the placeholder at the `at`-th `(` of the text, counted from 1. A placeholder runs
 *   from its `(` to the next `)` unless `length` says otherwise.
 * - `leave`: as for files: `true` or `"home"`, `"starlight"`, a package name, or `false` or `"stay"`.
 */
data class TextOptions(
    val markdown: Boolean = false,
    val spoilers: List<KakaoTextAttachment.Spoiler> = emptyList(),
    val emoticons: List<KakaoTextAttachment.Placement> = emptyList(),
    val leaveTo: LeaveTo? = null,
) {

    /** The attachment for [text], checked against it. */
    fun attachmentFor(text: String): KakaoTextAttachment =
        KakaoTextAttachment.of(text, markdown, spoilers, emoticons)

    companion object {

        private val KEYS = setOf("markdown", "spoilers", "emoticons", "leave")

        fun read(options: Any?): TextOptions {
            if (ScriptValues.isAbsent(options))
                return TextOptions()
            if (!ScriptValues.isObject(options))
                throw IllegalArgumentException("Options must be an object: { ${KEYS.joinToString()} }")
            ScriptValues.keys(options).firstOrNull { it !in KEYS }?.let { key ->
                throw IllegalArgumentException("Unknown option '$key'; options are ${KEYS.joinToString()}")
            }
            return TextOptions(
                markdown = ScriptValues.toBoolean(ScriptValues.member(options, "markdown")) ?: false,
                spoilers = arrayOption(ScriptValues.member(options, "spoilers"), "spoilers").map(::spoilerOf),
                emoticons = arrayOption(ScriptValues.member(options, "emoticons"), "emoticons").map(::placementOf),
                leaveTo = ScriptValues.member(options, "leave")?.let(SendOptions::leaveToOf),
            )
        }

        /** The items of an array option; empty when absent. */
        private fun arrayOption(value: Any?, name: String): List<Any?> {
            if (ScriptValues.isAbsent(value))
                return emptyList()
            return ScriptValues.asList(value) ?: throw IllegalArgumentException("$name must be an array")
        }

        private fun spoilerOf(value: Any?): KakaoTextAttachment.Spoiler {
            val pair = ScriptValues.asList(value)
            val (start, length) = when {
                pair != null && pair.size == 2         -> ScriptValues.toLong(pair[0]) to ScriptValues.toLong(pair[1])
                ScriptValues.isObject(value)           ->
                    ScriptValues.toLong(ScriptValues.member(value, "start")) to ScriptValues.toLong(ScriptValues.member(value, "length"))
                else                                   -> null to null
            }
            if (start == null || length == null)
                throw IllegalArgumentException("A spoiler is { start, length } or [start, length], with numbers")
            return KakaoTextAttachment.Spoiler(intOf(start, "The start of a spoiler"), intOf(length, "The length of a spoiler"))
        }

        /** [value] as an Int, refused when it doesn't fit rather than wrapped around. */
        private fun intOf(value: Long, what: String): Int {
            if (value !in Int.MIN_VALUE..Int.MAX_VALUE)
                throw IllegalArgumentException("$what, $value, is out of range")
            return value.toInt()
        }

        private fun placementOf(value: Any?): KakaoTextAttachment.Placement {
            ScriptValues.toText(value)?.let { return KakaoTextAttachment.Placement(it.trim()) }
            if (!ScriptValues.isObject(value))
                throw IllegalArgumentException("A mini emoticon is its ID, such as \"1234567_1\", or { id, at, length }")
            val id = ScriptValues.toText(ScriptValues.member(value, "id"))?.trim()
                ?: throw IllegalArgumentException("A mini emoticon { id, at, length } needs its id")
            return KakaoTextAttachment.Placement(
                id = id,
                at = ScriptValues.toLong(ScriptValues.member(value, "at"))?.let { intOf(it, "The at of mini emoticon '$id'") },
                length = ScriptValues.toLong(ScriptValues.member(value, "length"))?.let { intOf(it, "The length of mini emoticon '$id'") },
            )
        }
    }
}
