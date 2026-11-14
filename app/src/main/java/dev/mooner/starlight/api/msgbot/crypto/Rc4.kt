/*
 * Rc4.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot.crypto

/** The RC4 stream cipher; encrypting and decrypting are the same operation. */
internal object Rc4 {

    /**
     * [data] combined with the keystream of [key]. Every call starts the keystream again.
     *
     * @throws IllegalArgumentException When [key] isn't 1 to 256 bytes.
     */
    fun apply(key: ByteArray, data: ByteArray): ByteArray {
        require(key.isNotEmpty() && key.size <= 256) { "key must be between 1 and 256 bytes" }
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val t = s[i]; s[i] = s[j]; s[j] = t
        }
        val out = ByteArray(data.size)
        var a = 0
        var b = 0
        for (n in data.indices) {
            a = (a + 1) and 0xFF
            b = (b + s[a]) and 0xFF
            val t = s[a]; s[a] = s[b]; s[b] = t
            out[n] = (data[n].toInt() xor s[(s[a] + s[b]) and 0xFF]).toByte()
        }
        return out
    }
}
