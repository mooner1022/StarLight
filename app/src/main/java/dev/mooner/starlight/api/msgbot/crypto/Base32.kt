/*
 * Base32.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot.crypto

/** Base32 as RFC 4648 writes it (`A-Z2-7`, `=` padding, one line), with a forgiving decoder. */
internal object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size + 4) / 5 * 8)
        var i = 0
        while (i < data.size) {
            val n = minOf(5, data.size - i)
            var buffer = 0L
            for (k in 0 until 5)
                buffer = (buffer shl 8) or (if (k < n) (data[i + k].toLong() and 0xFF) else 0L)
            val chars = (n * 8 + 4) / 5
            for (k in 0 until 8) {
                if (k < chars) out.append(ALPHABET[((buffer shr (35 - 5 * k)) and 31).toInt()])
                else out.append('=')
            }
            i += 5
        }
        return out.toString()
    }

    /**
     * Reads letters of either case and `2`-`7`, skipping every other character, and stops at the first `=`. Missing
     * padding is fine, and bits that don't fill a byte are dropped. It never fails.
     */
    fun decode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=') break
            val v = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a'
                in '2'..'7' -> c - '2' + 26
                else        -> continue
            }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }
}
