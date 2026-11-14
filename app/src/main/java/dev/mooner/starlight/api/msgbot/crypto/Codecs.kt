/*
 * Codecs.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot.crypto

import java.util.Base64 as JavaBase64

/** Hex and Base64 in the forms the `Security` object writes and reads. */
internal object Codecs {

    private const val HEX_DIGITS = "0123456789abcdef"
    private const val MIME_LINE = 76

    /** Lowercase hex, two characters per byte. */
    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            out.append(HEX_DIGITS[(b.toInt() shr 4) and 0xF])
            out.append(HEX_DIGITS[b.toInt() and 0xF])
        }
        return out.toString()
    }

    /**
     * Hex read the way `Security` reads it: only lowercase digits count. A character that isn't one gives -1 for its
     * half, so `"zz"` is `0xFF` and `"zb"` is `0xFB` rather than an error.
     *
     * @throws StringIndexOutOfBoundsException When [text] has an odd length.
     */
    fun unhex(text: String): ByteArray {
        val out = ByteArray(text.length / 2)
        var i = 0
        while (i < text.length) {
            val hi = HEX_DIGITS.indexOf(text[i])
            val lo = HEX_DIGITS.indexOf(text[i + 1])
            out[i / 2] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return out
    }

    /**
     * Base64 as `android.util.Base64.DEFAULT` writes it: standard alphabet with padding, a line break after every 76
     * characters and one at the end (none for empty input).
     */
    fun base64(bytes: ByteArray): String {
        val flat = JavaBase64.getEncoder().encodeToString(bytes)
        val out = StringBuilder(flat.length + flat.length / MIME_LINE + 1)
        var i = 0
        while (i < flat.length) {
            val end = minOf(i + MIME_LINE, flat.length)
            out.append(flat, i, end).append('\n')
            i = end
        }
        return out.toString()
    }

    /**
     * Base64 read by `android.util.Base64.DEFAULT`: whitespace is skipped.
     *
     * @throws IllegalArgumentException When [text] has other characters than the alphabet or isn't complete Base64.
     */
    fun unbase64(text: String): ByteArray =
        android.util.Base64.decode(text, android.util.Base64.DEFAULT)

    /** Same as [unbase64], for text given as bytes. */
    fun unbase64(bytes: ByteArray): ByteArray =
        android.util.Base64.decode(bytes, android.util.Base64.DEFAULT)
}
