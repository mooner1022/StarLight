/*
 * Aria.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot.crypto

import java.math.BigInteger

/**
 * The ARIA block cipher with a 128-bit key (RFC 5794), 12 rounds, and ECB mode with PKCS#7 padding on top of it.
 */
internal class Aria128(key: ByteArray) {

    private val encryptionKeys: Array<ByteArray>
    private val decryptionKeys: Array<ByteArray>

    init {
        require(key.size == 16) { "ARIA-128 needs a 16-byte key" }
        val w0 = BigInteger(1, key)
        val w1 = BigInteger(1, odd(toBlock(w0), CK1))
        val w2 = BigInteger(1, even(toBlock(w1), CK2)).xor(w0)
        val w3 = BigInteger(1, odd(toBlock(w2), CK3)).xor(w1)
        encryptionKeys = arrayOf(
            w0.xor(rotateRight(w1, 19)),
            w1.xor(rotateRight(w2, 19)),
            w2.xor(rotateRight(w3, 19)),
            rotateRight(w0, 19).xor(w3),
            w0.xor(rotateRight(w1, 31)),
            w1.xor(rotateRight(w2, 31)),
            w2.xor(rotateRight(w3, 31)),
            rotateRight(w0, 31).xor(w3),
            w0.xor(rotateRight(w1, -61)),
            w1.xor(rotateRight(w2, -61)),
            w2.xor(rotateRight(w3, -61)),
            rotateRight(w0, -61).xor(w3),
            w0.xor(rotateRight(w1, -31)),
        ).map { toBlock(it) }.toTypedArray()
        decryptionKeys = Array(ROUND_KEYS) { i ->
            when (i) {
                0 -> encryptionKeys[ROUND_KEYS - 1].copyOf()
                ROUND_KEYS - 1 -> encryptionKeys[0].copyOf()
                else -> diffuse(encryptionKeys[ROUND_KEYS - 1 - i])
            }
        }
    }

    /** Encrypts the 16 bytes of [input] from [inOffset] into [output] at [outOffset]. */
    fun encryptBlock(input: ByteArray, inOffset: Int, output: ByteArray, outOffset: Int) =
        crypt(encryptionKeys, input, inOffset, output, outOffset)

    /** Decrypts the 16 bytes of [input] from [inOffset] into [output] at [outOffset]. */
    fun decryptBlock(input: ByteArray, inOffset: Int, output: ByteArray, outOffset: Int) =
        crypt(decryptionKeys, input, inOffset, output, outOffset)

    /** [data] padded as PKCS#7 (a whole block is added when it is already a multiple of 16), encrypted block by block. */
    fun encryptEcb(data: ByteArray): ByteArray {
        val pad = BLOCK - data.size % BLOCK
        val padded = data.copyOf(data.size + pad)
        padded.fill(pad.toByte(), data.size, padded.size)
        val out = ByteArray(padded.size)
        for (i in padded.indices step BLOCK)
            encryptBlock(padded, i, out, i)
        return out
    }

    /**
     * [data] decrypted block by block, then unpadded.
     *
     * @return The plain bytes, or `null` when the last byte isn't a pad length from 1 to 16 or the pad bytes differ.
     * @throws IllegalArgumentException When [data] is empty or not a multiple of 16 bytes.
     */
    fun decryptEcb(data: ByteArray): ByteArray? {
        require(data.isNotEmpty() && data.size % BLOCK == 0) { "ARIA data must be a multiple of 16 bytes" }
        val out = ByteArray(data.size)
        for (i in data.indices step BLOCK)
            decryptBlock(data, i, out, i)
        val pad = out[out.size - 1].toInt() and 0xFF
        if (pad < 1 || pad > BLOCK) return null
        for (i in out.size - pad until out.size)
            if (out[i].toInt() and 0xFF != pad) return null
        return out.copyOf(out.size - pad)
    }

    private fun crypt(keys: Array<ByteArray>, input: ByteArray, inOffset: Int, output: ByteArray, outOffset: Int) {
        var state = input.copyOfRange(inOffset, inOffset + BLOCK)
        for (round in 0 until ROUNDS - 1)
            state = if (round % 2 == 0) odd(state, keys[round]) else even(state, keys[round])
        val last = keys[ROUNDS - 1]
        val end = keys[ROUNDS]
        for (i in 0 until BLOCK)
            output[outOffset + i] = (AriaTables.second[i % 4][(state[i].toInt() xor last[i].toInt()) and 0xFF] xor end[i].toInt()).toByte()
    }

    private companion object {
        const val BLOCK = 16
        const val ROUNDS = 12
        const val ROUND_KEYS = 13

        val CK1 = hex("517cc1b727220a94fe13abe8fa9a6ee0")
        val CK2 = hex("6db14acc9e21c820ff28b1d5ef5de2b0")
        val CK3 = hex("db92371d2126e9700324977504e8c90e")

        fun hex(text: String): ByteArray = toBlock(BigInteger(text, 16))

        fun toBlock(value: BigInteger): ByteArray {
            val raw = value.toByteArray()
            val out = ByteArray(BLOCK)
            val n = minOf(raw.size, BLOCK)
            System.arraycopy(raw, raw.size - n, out, BLOCK - n, n)
            return out
        }

        /** A 128-bit rotation to the right by [bits]; a negative count rotates left. */
        fun rotateRight(value: BigInteger, bits: Int): BigInteger {
            val s = ((bits % 128) + 128) % 128
            val mask = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
            return value.shiftRight(s).or(value.shiftLeft(128 - s)).and(mask)
        }

        /** Odd round: key mixing, substitution layer 1, diffusion. */
        fun odd(state: ByteArray, key: ByteArray): ByteArray {
            val t = ByteArray(BLOCK) { i -> AriaTables.first[i % 4][(state[i].toInt() xor key[i].toInt()) and 0xFF].toByte() }
            return diffuse(t)
        }

        /** Even round: key mixing, substitution layer 2, diffusion. */
        fun even(state: ByteArray, key: ByteArray): ByteArray {
            val t = ByteArray(BLOCK) { i -> AriaTables.second[i % 4][(state[i].toInt() xor key[i].toInt()) and 0xFF].toByte() }
            return diffuse(t)
        }

        private val DIFFUSION = arrayOf(
            intArrayOf(3, 4, 6, 8, 9, 13, 14), intArrayOf(2, 5, 7, 8, 9, 12, 15),
            intArrayOf(1, 4, 6, 10, 11, 12, 15), intArrayOf(0, 5, 7, 10, 11, 13, 14),
            intArrayOf(0, 2, 5, 8, 11, 14, 15), intArrayOf(1, 3, 4, 9, 10, 14, 15),
            intArrayOf(0, 2, 7, 9, 10, 12, 13), intArrayOf(1, 3, 6, 8, 11, 12, 13),
            intArrayOf(0, 1, 4, 7, 10, 13, 15), intArrayOf(0, 1, 5, 6, 11, 12, 14),
            intArrayOf(2, 3, 5, 6, 8, 13, 15), intArrayOf(2, 3, 4, 7, 9, 12, 14),
            intArrayOf(1, 2, 6, 7, 9, 11, 12), intArrayOf(0, 3, 6, 7, 8, 10, 13),
            intArrayOf(0, 3, 4, 5, 9, 11, 14), intArrayOf(1, 2, 4, 5, 8, 10, 15),
        )

        /** The diffusion layer, a 16 by 16 binary matrix that is its own inverse. */
        fun diffuse(x: ByteArray): ByteArray = ByteArray(BLOCK) { row ->
            var v = 0
            for (i in DIFFUSION[row]) v = v xor x[i].toInt()
            v.toByte()
        }
    }
}

/** The substitution boxes of ARIA, worked out from their definitions in RFC 5794. */
private object AriaTables {

    /** What the linear part of S2 makes of each bit of its input, bit 0 first. */
    private val AFFINE_COLUMNS = intArrayOf(0xac, 0xc5, 0x12, 0xcf, 0x5b, 0x5f, 0x85, 0xee)

    private val s1: IntArray
    private val s2: IntArray
    private val x1: IntArray
    private val x2: IntArray

    /** Substitution layer 1 by byte position modulo 4: S1, S2, X1, X2. */
    val first: Array<IntArray>

    /** Substitution layer 2 by byte position modulo 4: X1, X2, S1, S2. */
    val second: Array<IntArray>

    init {
        s1 = IntArray(256)
        s2 = IntArray(256)
        for (x in 0 until 256) {
            val inverse = power(x, 254)
            s1[x] = inverse xor rotl(inverse, 1) xor rotl(inverse, 2) xor rotl(inverse, 3) xor rotl(inverse, 4) xor 0x63
            val p = power(x, 247)
            var v = 0xE2
            for (k in 0 until 8)
                if ((p shr k) and 1 == 1) v = v xor AFFINE_COLUMNS[k]
            s2[x] = v
        }
        x1 = IntArray(256)
        x2 = IntArray(256)
        for (i in 0 until 256) {
            x1[s1[i]] = i
            x2[s2[i]] = i
        }
        first = arrayOf(s1, s2, x1, x2)
        second = arrayOf(x1, x2, s1, s2)
    }

    private fun rotl(x: Int, n: Int): Int = ((x shl n) or (x shr (8 - n))) and 0xFF

    private fun multiply(a: Int, b: Int): Int {
        var x = a
        var y = b
        var r = 0
        while (y != 0) {
            if (y and 1 == 1) r = r xor x
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11b
            y = y shr 1
        }
        return r
    }

    /** [x] to the [exponent] in GF(2^8), 0 for 0. */
    private fun power(x: Int, exponent: Int): Int {
        if (x == 0) return 0
        var r = 1
        repeat(exponent) { r = multiply(r, x) }
        return r
    }
}
