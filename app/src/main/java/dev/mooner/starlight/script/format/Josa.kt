/*
 * Josa.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.format

/** Korean particles (조사) that change with the word before them, for `Format.josa`. */
internal object Josa {

    /** Pairs whose first form follows a final consonant (받침), in either order. */
    private val PAIRS = listOf("은" to "는", "이" to "가", "을" to "를", "과" to "와", "아" to "야")

    /** Passed over at the end of a word: spaces, closing brackets and quotes, and punctuation. */
    private const val SKIPPED = ")]}\"'」』〉》.,!?~…"

    private const val HANGUL_FIRST = 0xAC00
    private const val HANGUL_LAST = 0xD7A3
    private const val FINALS = 28
    private const val FINAL_RIEUL = 8

    private enum class Final { NONE, CONSONANT, RIEUL }

    /**
     * [word] with the form of [pair] that fits it: `attach("사과", "을/를")` gives `사과를`.
     *
     * @param pair Two forms split by `/`, as `을/를` or `으로/로`.
     * @throws IllegalArgumentException When [pair] isn't two forms.
     */
    fun attach(word: String, pair: String): String {
        val pieces = pair.split('/')
        require(pieces.size == 2 && pieces.all(String::isNotEmpty)) {
            "Format.josa takes two forms split by '/', as \"을/를\": \"$pair\" isn't"
        }
        val (afterConsonant, afterVowel) = order(pieces[0], pieces[1])
        val final = finalOf(word)
        val takesConsonantForm = when (final) {
            Final.NONE      -> false
            Final.CONSONANT -> true
            // 서울로, not 서울으로: ㄹ counts as no final consonant before 으.
            Final.RIEUL     -> !afterConsonant.startsWith('으')
        }
        return word + if (takesConsonantForm) afterConsonant else afterVowel
    }

    /** The two forms as (after a final consonant, after a vowel). */
    private fun order(first: String, second: String): Pair<String, String> {
        for ((consonant, vowel) in PAIRS) {
            if (first == consonant && second == vowel)
                return first to second
            if (first == vowel && second == consonant)
                return second to first
        }
        if (isLinked(first, second))
            return first to second
        if (isLinked(second, first))
            return second to first
        return first to second
    }

    /** Whether [longer] is [shorter] with 이 or 으 before it, as 이랑 and 랑, or 으로 and 로. */
    private fun isLinked(longer: String, shorter: String): Boolean =
        (longer.startsWith('이') || longer.startsWith('으')) && longer.substring(1) == shorter

    private fun finalOf(word: String): Final {
        var index = word.length - 1
        while (index >= 0 && (word[index].isWhitespace() || word[index] in SKIPPED))
            index--
        if (index < 0)
            return Final.NONE
        val c = word[index]
        return when {
            c.code in HANGUL_FIRST..HANGUL_LAST -> when ((c.code - HANGUL_FIRST) % FINALS) {
                0           -> Final.NONE
                FINAL_RIEUL -> Final.RIEUL
                else        -> Final.CONSONANT
            }
            c in '0'..'9' -> when (c) {
                '1', '7', '8' -> Final.RIEUL
                '0', '3', '6' -> Final.CONSONANT
                else          -> Final.NONE
            }
            c in 'a'..'z' || c in 'A'..'Z' -> {
                val lower = c.lowercaseChar()
                val before = word.getOrNull(index - 1)?.lowercaseChar()
                when {
                    lower == 'g' && before == 'n' -> Final.CONSONANT
                    lower == 'l'                  -> Final.RIEUL
                    lower in "mnbckpt"            -> Final.CONSONANT
                    else                          -> Final.NONE
                }
            }
            else -> Final.NONE
        }
    }
}
