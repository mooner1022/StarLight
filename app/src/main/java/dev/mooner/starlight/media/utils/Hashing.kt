/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.utils

import java.security.MessageDigest

private val HEX = "0123456789abcdef".toCharArray()

fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    for (i in indices) {
        val b = this[i].toInt() and 0xFF
        chars[i * 2] = HEX[b ushr 4]
        chars[i * 2 + 1] = HEX[b and 0x0F]
    }
    return String(chars)
}

fun sha256Hex(vararg parts: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .apply { parts.forEach(::update) }
        .digest()
        .toHex()
