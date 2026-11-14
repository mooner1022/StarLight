/*
 * KakaoTextAttachment.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.media.send

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What KakaoTalk's share screen adds to a shared text: Markdown, spoilers and mini emoticons, written as the message
 * attachment KakaoTalk reads from a share.
 *
 * KakaoTalk drops a part it can't use without saying so: a mini emoticon the account doesn't have is sent as its
 * placeholder text. So the parts are checked here first, and a mistake throws with what is wrong.
 */
data class KakaoTextAttachment(
    /** Whether the text is Markdown, which KakaoTalk shows formatted. */
    val markdown: Boolean = false,
    /** Ranges of the text shown hidden until tapped. */
    val spoilers: List<Spoiler> = emptyList(),
    /** Mini emoticons, each shown in place of a placeholder in the text. */
    val emoticons: List<Emoticon> = emptyList(),
) {

    /** [length] characters of the text from [start], counted from 0, hidden until tapped. */
    data class Spoiler(val start: Int, val length: Int)

    /**
     * The mini emoticon [id] (`"<item code>_<number>"`), shown in place of the placeholder at each of [at]: the
     * placeholder starting at the n-th `(` of the text, counted from 1, and [length] characters long.
     */
    data class Emoticon(val id: String, val at: List<Int>, val length: Int)

    /** Whether there is nothing to add, so the text is sent as it is. */
    val isEmpty: Boolean
        get() = !markdown && spoilers.isEmpty() && emoticons.isEmpty()

    /** The attachment as KakaoTalk reads it, or null when there is nothing to add. */
    fun toJson(): String? {
        if (isEmpty)
            return null
        return buildJsonObject {
            if (markdown)
                put("markdown", true)
            if (spoilers.isNotEmpty())
                put("spoilers", JsonArray(spoilers.map { buildJsonObject { put("loc", it.start); put("len", it.length) } }))
            if (emoticons.isNotEmpty())
                put("emojis", buildJsonObject {
                    put("total_item", emoticons.sumOf { it.at.size })
                    put("total_len", emoticons.sumOf { it.length * it.at.size })
                    put("items", JsonArray(emoticons.map { emoticon ->
                        buildJsonObject {
                            put("id", emoticon.id)
                            put("len", emoticon.length)
                            put("at", buildJsonArray { emoticon.at.forEach { add(JsonPrimitive(it)) } })
                        }
                    }))
                })
        }.toString()
    }

    /** A mini emoticon as a script places it: by its placeholder's `(`, or the next placeholder when [at] is null. */
    data class Placement(val id: String, val at: Int? = null, val length: Int? = null)

    companion object {

        /** The most mini emoticons KakaoTalk takes in one message. */
        const val MAX_EMOTICONS = 150

        /** How long a mini emoticon's placeholder may be, as KakaoTalk counts it. */
        val PLACEHOLDER_LENGTHS = 1..12

        private val EMOTICON_ID = Regex("""\d+_\d+""")

        /**
         * The attachment for [text], checked against it.
         *
         * A [Placement] without `at` takes the next placeholder: the next `(` not taken yet. A placeholder runs from its
         * `(` to the first `)` after it, unless the placement gives its length.
         *
         * @throws IllegalArgumentException When there are spoilers in a Markdown text, a spoiler is outside the text, an
         * emoticon ID isn't
         * `"<item code>_<number>"`, there is no placeholder left, a placeholder is too long or unclosed, two
         * emoticons take the same placeholder, or there are more than [MAX_EMOTICONS].
         */
        fun of(text: String, markdown: Boolean, spoilers: List<Spoiler>, placements: List<Placement>): KakaoTextAttachment {
            // KakaoTalk drops the spoilers of a Markdown message and shows the text plainly.
            require(!markdown || spoilers.isEmpty()) {
                "KakaoTalk shows no spoilers in a Markdown message: send them without markdown"
            }
            for (spoiler in spoilers) {
                require(spoiler.start >= 0 && spoiler.length > 0 && spoiler.start + spoiler.length <= text.length) {
                    "Spoiler (start ${spoiler.start}, length ${spoiler.length}) is outside the text of ${text.length} characters"
                }
            }
            return KakaoTextAttachment(markdown, spoilers, emoticonsOf(text, placements))
        }

        private fun emoticonsOf(text: String, placements: List<Placement>): List<Emoticon> {
            if (placements.isEmpty())
                return emptyList()
            require(placements.size <= MAX_EMOTICONS) {
                "KakaoTalk takes at most $MAX_EMOTICONS mini emoticons in a message, not ${placements.size}"
            }
            val opens = text.indices.filter { text[it] == '(' }
            val taken = HashSet<Int>()
            var next = 1
            // Same ID and length: one item with several places, as KakaoTalk writes them.
            val byIdAndLength = LinkedHashMap<Pair<String, Int>, MutableList<Int>>()
            for (placement in placements) {
                require(EMOTICON_ID.matches(placement.id)) {
                    "Mini emoticon ID '${placement.id}' isn't \"<item code>_<number>\", such as \"1234567_1\""
                }
                val ordinal = placement.at ?: run {
                    while (next in taken) next++
                    next
                }
                require(ordinal in 1..opens.size) {
                    if (placement.at == null) "No placeholder left for mini emoticon '${placement.id}': the text has ${opens.size} '('"
                    else "Mini emoticon '${placement.id}' is at the ${placement.at}th '(', but the text has ${opens.size}"
                }
                require(taken.add(ordinal)) { "Two mini emoticons are at the ${ordinal}th '('" }
                val start = opens[ordinal - 1]
                val length = placement.length ?: run {
                    val close = text.indexOf(')', start + 1)
                    require(close >= 0) { "The placeholder at the ${ordinal}th '(' has no ')'" }
                    close - start + 1
                }
                require(length in PLACEHOLDER_LENGTHS && start + length <= text.length) {
                    "The placeholder of mini emoticon '${placement.id}' is $length characters; KakaoTalk takes " +
                        "${PLACEHOLDER_LENGTHS.first} to ${PLACEHOLDER_LENGTHS.last}, within the text"
                }
                byIdAndLength.getOrPut(placement.id to length, ::mutableListOf) += ordinal
            }
            return byIdAndLength.map { (key, at) -> Emoticon(key.first, at.sorted(), key.second) }
        }
    }
}
