/*
 * PbeMd5Des.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption in the format of jasypt's default string encryptor: PBEWithMD5AndDES (PKCS#5 version 1,
 * MD5, 1000 iterations) with a random 8-byte salt written in front of the cipher text, as Base64 on one line.
 */
internal object PbeMd5Des {

    private const val SALT_SIZE = 8
    private const val ITERATIONS = 1000
    private val random = SecureRandom()

    /**
     * Encrypts [plain] (as UTF-8) under [password]. The result differs on every call, because the salt does.
     *
     * @throws IllegalArgumentException When [password] is empty.
     */
    fun encrypt(password: String, plain: String): String {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        return encrypt(password, plain, salt)
    }

    internal fun encrypt(password: String, plain: String, salt: ByteArray): String {
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt)
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(salt + body)
    }

    /**
     * Decrypts what [encrypt] wrote, or anything in the same format.
     *
     * @throws IllegalStateException When [encrypted] can't be decrypted with [password].
     * @throws IllegalArgumentException When [password] is empty.
     */
    fun decrypt(password: String, encrypted: String): String {
        require(password.isNotEmpty()) { "Password cannot be set empty" }
        try {
            val all = java.util.Base64.getMimeDecoder().decode(encrypted.toByteArray(Charsets.US_ASCII))
            if (all.size <= SALT_SIZE) throw IllegalStateException()
            val cipher = cipher(Cipher.DECRYPT_MODE, password, all.copyOfRange(0, SALT_SIZE))
            return String(cipher.doFinal(all, SALT_SIZE, all.size - SALT_SIZE), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Encryption operation not possible", e)
        } catch (e: java.security.GeneralSecurityException) {
            throw IllegalStateException("Encryption operation not possible", e)
        } catch (e: IllegalStateException) {
            throw IllegalStateException("Encryption operation not possible", e)
        }
    }

    private fun cipher(mode: Int, password: String, salt: ByteArray): Cipher {
        require(password.isNotEmpty()) { "Password cannot be set empty" }
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFC)
        // PKCS#5 version 1 takes one byte per password character: the low byte of the character.
        var digest = ByteArray(normalized.length) { normalized[it].code.toByte() } + salt
        val md5 = MessageDigest.getInstance("MD5")
        repeat(ITERATIONS) { digest = md5.digest(digest) }
        val cipher = Cipher.getInstance("DES/CBC/PKCS5Padding")
        cipher.init(mode, SecretKeySpec(digest.copyOfRange(0, 8), "DES"), IvParameterSpec(digest.copyOfRange(8, 16)))
        return cipher
    }
}
