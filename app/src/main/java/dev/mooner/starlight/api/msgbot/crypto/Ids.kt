/*
 * Ids.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot.crypto

import java.util.UUID

/** The layouts of the sortable IDs that `Security.ulid()` and `Security.uuidv7()` write. */
internal object Ids {

    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TIME_LIMIT_MASK = -281474976710656L

    /**
     * A ULID: 10 characters of [millis] since the epoch, then 16 characters of the 10 bytes in [random], in Crockford
     * Base32, uppercase.
     *
     * @throws IllegalArgumentException When [millis] doesn't fit 48 bits.
     */
    fun ulid(millis: Long, random: ByteArray): String {
        require(random.size == 10) { "a ULID needs 10 random bytes" }
        if (millis and TIME_LIMIT_MASK != 0L)
            throw IllegalArgumentException("ULID does not support timestamps after +10889-08-02T05:31:50.655Z!")
        val out = CharArray(26)
        for (i in 0 until 10)
            out[i] = CROCKFORD[((millis ushr ((9 - i) * 5)) and 31).toInt()]
        for (group in 0 until 2) {
            var bits = 0L
            for (k in 0 until 5)
                bits = (bits shl 8) or (random[group * 5 + k].toLong() and 0xFF)
            for (i in 0 until 8)
                out[10 + group * 8 + i] = CROCKFORD[((bits ushr ((7 - i) * 5)) and 31).toInt()]
        }
        return String(out)
    }

    /**
     * A version 7 UUID: [millis] in the first 48 bits, the version and variant bits set, and the rest from the 16
     * bytes in [random].
     */
    fun uuidV7(millis: Long, random: ByteArray): String {
        require(random.size == 16) { "a UUID needs 16 random bytes" }
        val b = random.copyOf()
        for (i in 0 until 6)
            b[i] = (millis ushr ((5 - i) * 8)).toByte()
        b[6] = ((b[6].toInt() and 0x0F) or 0x70).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) msb = (msb shl 8) or (b[i].toLong() and 0xFF)
        for (i in 8 until 16) lsb = (lsb shl 8) or (b[i].toLong() and 0xFF)
        return UUID(msb, lsb).toString()
    }
}
