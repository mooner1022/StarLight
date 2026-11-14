/*
 * SecurityApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.msgbot.crypto.Aria128
import dev.mooner.starlight.api.msgbot.crypto.Base32
import dev.mooner.starlight.api.msgbot.crypto.Codecs
import dev.mooner.starlight.api.msgbot.crypto.Ids
import dev.mooner.starlight.api.msgbot.crypto.PbeMd5Des
import dev.mooner.starlight.api.msgbot.crypto.Rc4
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Suppress("unused")
class SecurityApi: Api<SecurityApi.Security>() {

    /**
     * The `Security` global: encryption, encoding, hashing and ID generation; a global in legacy and API2 projects.
     *
     * Every method runs on the calling thread. Arguments are read as text: a number is its text, and `null` or
     * `undefined` throws `"<name> must be a string"` (`"key"`, `"value"`, `"seed"`, `"curve"`). A failing operation
     * throws instead of returning `null` (a wrong key, bad Base64, an unsupported algorithm), except where a method
     * says otherwise.
     *
     * Binary results are Base64 as Android writes it by default, which breaks a line after 76 characters and ends
     * with a line break: `Security.base64Encode("a")` is `"YQ==\n"`. Call `trim()` on a result you compare or
     * store, and keep in mind that a trimmed key may then be read differently by the AES methods (see `aesEncode`).
     * Hex results are lowercase; where a method reads hex, only lowercase digits count, and any other character
     * reads as if it were a missing half of a byte rather than throwing.
     *
     * Algorithms that the device's Android version doesn't offer (`md2`, the `sha3_*` methods, `eccEncode`) throw.
     *
     * ```js
     * const key = Security.generateAESKey();
     * const iv = Security.generateIV();
     * const secret = Security.aesEncode(key, iv, "hello");
     * Security.aesDecode(key, iv, secret); // "hello"
     * ```
     */
    class Security internal constructor(private val json: ScriptJson) {

        private val random = SecureRandom()

        // region AES

        /**
         * Encrypts [value] with AES in CBC mode with PKCS#5 padding.
         *
         * [key] and [initVector] are text. A key is used as its UTF-8 bytes when they are 16, 24 or 32 long, and an
         * initialisation vector when they are 16 long. Only when they aren't is the text read as Base64 (what
         * `generateAESKey` and `generateIV` give) and used if that has a valid length. Beware that a `trim()`med
         * 128-bit or 192-bit key from `generateAESKey` has a valid length as text, and is then used as text.
         *
         * @param key The key text.
         * @param initVector The initialisation vector text.
         * @param value The text to encrypt, as UTF-8.
         * @return The cipher text as Base64 with line breaks.
         * @throws java.security.InvalidKeyException When [key] has no valid length.
         * @throws java.security.InvalidAlgorithmParameterException When [initVector] has no valid length.
         */
        fun aesEncode(key: Any?, initVector: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val iv = ScriptArgs.string(initVector, "initVector")
            val text = ScriptArgs.string(value, "value")
            val ivBytes = bytesOrBase64(iv) { it.size == 16 }
            val keyBytes = bytesOrBase64(k) { it.size == 16 || it.size == 24 || it.size == 32 }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            return Codecs.base64(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
        }

        /**
         * Decrypts the Base64 text [value] that `aesEncode` wrote.
         *
         * @param key The key text, read as in `aesEncode`.
         * @param initVector The initialisation vector text, read as in `aesEncode`.
         * @param value The cipher text as Base64.
         * @return The plain text.
         * @throws IllegalArgumentException When [value] isn't Base64.
         * @throws javax.crypto.BadPaddingException When [key] or [initVector] isn't the one used to encrypt.
         */
        fun aesDecode(key: Any?, initVector: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val iv = ScriptArgs.string(initVector, "initVector")
            val text = ScriptArgs.string(value, "value")
            val ivBytes = bytesOrBase64(iv) { it.size == 16 }
            val keyBytes = bytesOrBase64(k) { it.size == 16 || it.size == 24 || it.size == 32 }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            return String(cipher.doFinal(Codecs.unbase64(text)), Charsets.UTF_8)
        }

        /**
         * A new random AES key as Base64 text, to give `aesEncode` and `aesDecode`.
         *
         * @param keySize 128, 192 or 256 bits. 256 when left out, `null`, or not a number.
         * @throws java.security.InvalidParameterException When the size isn't one AES has.
         */
        @JvmOverloads
        fun generateAESKey(keySize: Any? = null): String {
            val generator = KeyGenerator.getInstance("AES")
            generator.init(ScriptArgs.int(keySize, "keySize", 256))
            return Codecs.base64(generator.generateKey().encoded)
        }

        /**
         * A new random initialisation vector as Base64 text; the same as [generateRandomBytes] with a default length.
         *
         * @param length The number of random bytes. 16 when left out, `null`, or not a number; AES needs 16.
         */
        @JvmOverloads
        fun generateIV(length: Any? = null): String =
            randomBytes(ScriptArgs.int(length, "length", 16))

        /**
         * [length] random bytes from a secure random generator, as Base64 text.
         *
         * @param length The number of bytes.
         * @throws IllegalArgumentException `"length must be a number"` when it isn't one.
         * @throws NegativeArraySizeException When it is negative.
         */
        fun generateRandomBytes(length: Any?): String =
            randomBytes(ScriptArgs.int(length, "length"))

        // endregion

        // region ARIA

        /**
         * Encrypts [value] with ARIA-128 in ECB mode, with PKCS#7 padding.
         *
         * The cipher key is the MD5 hash of the UTF-8 bytes of [key].
         *
         * @param key The key text.
         * @param value The text to encrypt, as UTF-8.
         * @return The cipher text as Base64 with line breaks.
         */
        fun ariaEncode(key: Any?, value: Any?): String {
            val cipher = ariaOf(ScriptArgs.string(key, "key"))
            val text = ScriptArgs.string(value, "value")
            return Codecs.base64(cipher.encryptEcb(text.toByteArray(Charsets.UTF_8)))
        }

        /**
         * Decrypts the Base64 text [value] that `ariaEncode` wrote.
         *
         * @param key The key text.
         * @param value The cipher text as Base64.
         * @return The plain text, or `null` when the padding after decrypting is wrong, which is what a wrong key
         * gives almost every time.
         * @throws IllegalArgumentException When [value] isn't Base64, is empty, or isn't a whole number of 16-byte
         * blocks.
         */
        fun ariaDecode(key: Any?, value: Any?): String? {
            val cipher = ariaOf(ScriptArgs.string(key, "key"))
            val text = ScriptArgs.string(value, "value")
            val plain = cipher.decryptEcb(Codecs.unbase64(text)) ?: return null
            return String(plain, Charsets.UTF_8)
        }

        // endregion

        // region Base64 and Base32

        /**
         * [value] as Base64, with a line break after every 76 characters and at the end.
         *
         * @param value The text, encoded as UTF-8.
         */
        fun base64Encode(value: Any?): String =
            Codecs.base64(ScriptArgs.string(value, "value").toByteArray(Charsets.UTF_8))

        /**
         * The text that Base64 [value] stands for, read as UTF-8; whitespace in [value] is skipped.
         *
         * @param value The Base64 text.
         * @throws IllegalArgumentException When [value] isn't valid Base64.
         */
        fun base64Decode(value: Any?): String =
            String(Codecs.unbase64(ScriptArgs.string(value, "value").toByteArray(Charsets.UTF_8)), Charsets.UTF_8)

        /**
         * [value] as Base32 (RFC 4648): uppercase `A-Z` and `2-7`, padded with `=`, in one line.
         *
         * @param value The text, encoded as UTF-8.
         */
        fun base32Encode(value: Any?): String =
            Base32.encode(ScriptArgs.string(value, "value").toByteArray(Charsets.UTF_8))

        /**
         * The text that Base32 [value] stands for, read as UTF-8.
         *
         * This never throws on bad input: letters of either case and `2`-`7` count, every other character is skipped,
         * decoding stops at the first `=`, and padding may be missing.
         *
         * @param value The Base32 text.
         */
        fun base32Decode(value: Any?): String =
            String(Base32.decode(ScriptArgs.string(value, "value")), Charsets.UTF_8)

        // endregion

        // region DES and Triple DES

        /**
         * Encrypts [value] with Triple DES in CBC mode with PKCS#5 padding and a zero initialisation vector.
         *
         * The cipher key is derived from [key]: the MD5 hash of its UTF-8 bytes, padded to 24 bytes by repeating the
         * first 8.
         *
         * @param key The key text.
         * @param value The text to encrypt, as UTF-8.
         * @return The cipher text as lowercase hex.
         */
        fun des3Encode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val text = ScriptArgs.string(value, "value")
            val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(des3Key(k), "DESede"), IvParameterSpec(ByteArray(8)))
            return Codecs.hex(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
        }

        /**
         * Decrypts the hex text [value] that `des3Encode` wrote.
         *
         * Only lowercase hex digits count; an odd length throws.
         *
         * @param key The key text.
         * @param value The cipher text as lowercase hex.
         * @return The plain text.
         * @throws IllegalArgumentException When [value] is `null` or `undefined`.
         * @throws javax.crypto.BadPaddingException When [key] isn't the one used to encrypt.
         */
        fun des3Decode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val text = ScriptArgs.stringOrNull(value) ?: throw IllegalArgumentException("Input buffer cannot be null")
            val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(des3Key(k), "DESede"), IvParameterSpec(ByteArray(8)))
            return String(cipher.doFinal(Codecs.unhex(text)), Charsets.UTF_8)
        }

        /**
         * Encrypts [value] with single DES (the device's default DES mode, ECB with padding).
         *
         * @param key A DES key as Base64 text, such as `desKey` gives: 8 bytes once decoded.
         * @param value The text to encrypt, as UTF-8.
         * @return The cipher text as Base64 with line breaks.
         * @throws java.security.InvalidKeyException When [key] isn't 8 bytes.
         */
        fun desEncode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val (encrypt, _) = desCiphers(k)
            val text = ScriptArgs.string(value, "value")
            return Codecs.base64(encrypt.doFinal(text.toByteArray(Charsets.UTF_8)))
        }

        /**
         * Decrypts the Base64 text [value] that `desEncode` wrote.
         *
         * @param key The DES key as Base64 text.
         * @param value The cipher text as Base64.
         * @return The plain text.
         * @throws java.security.InvalidKeyException When [key] isn't 8 bytes.
         * @throws javax.crypto.BadPaddingException When [key] isn't the one used to encrypt.
         */
        fun desDecode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val (_, decrypt) = desCiphers(k)
            val text = ScriptArgs.string(value, "value")
            return String(decrypt.doFinal(Codecs.unbase64(text)), Charsets.UTF_8)
        }

        /** A new random DES key as Base64 text, to give `desEncode` and `desDecode`. */
        fun desKey(): String =
            Codecs.base64(KeyGenerator.getInstance("DES").generateKey().encoded)

        // endregion

        // region ECC

        /**
         * A new elliptic-curve key pair on [curve].
         *
         * @param curve A curve name, such as `"secp256r1"`.
         * @return An object with two Base64 texts: `public` (X.509) for `eccEncode` and `private` (PKCS#8) for
         * `eccDecode`.
         * @throws java.security.InvalidAlgorithmParameterException When the device doesn't know the curve.
         */
        fun eccKey(curve: Any?): Any? {
            val name = ScriptArgs.string(curve, "curve")
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec(name))
            val pair = generator.generateKeyPair()
            val result = JsonObject(mapOf(
                "public" to JsonPrimitive(Codecs.base64(pair.public.encoded)),
                "private" to JsonPrimitive(Codecs.base64(pair.private.encoded)),
            ))
            return json.parse(result.toString())
        }

        /**
         * Encrypts [value] with an elliptic-curve public key, using the device's `ECIESwithAES` scheme.
         *
         * The result differs on every call. Whether other devices or apps can decrypt it depends on the device's
         * scheme settings; `eccDecode` on the same device always can.
         *
         * @param key A public key as Base64 text, from `eccKey`.
         * @param value The text to encrypt, as UTF-8.
         * @return The cipher text as lowercase hex.
         * @throws java.security.NoSuchAlgorithmException When the device doesn't offer the scheme.
         */
        fun eccEncode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val text = ScriptArgs.string(value, "value")
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Codecs.unbase64(k)))
            val cipher = Cipher.getInstance("ECIESwithAES")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            return Codecs.hex(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
        }

        /**
         * Decrypts the hex text [value] that `eccEncode` wrote.
         *
         * Only lowercase hex digits count; an odd length throws.
         *
         * @param key A private key as Base64 text, from `eccKey`.
         * @param value The cipher text as lowercase hex.
         * @return The plain text.
         * @throws java.security.NoSuchAlgorithmException When the device doesn't offer the scheme.
         */
        fun eccDecode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val text = ScriptArgs.string(value, "value")
            val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Codecs.unbase64(k)))
            val cipher = Cipher.getInstance("ECIESwithAES")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            return String(cipher.doFinal(Codecs.unhex(text)), Charsets.UTF_8)
        }

        // endregion

        // region RC4 and password encryption

        /**
         * Encrypts [value] with RC4.
         *
         * @param key The key text; its UTF-8 form is used as it is and must be 1 to 256 bytes.
         * @param value The text to encrypt, as UTF-8.
         * @return The cipher text as lowercase hex.
         * @throws IllegalArgumentException `"key must be between 1 and 256 bytes"` for a key of another size.
         */
        fun rc4Encode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val text = ScriptArgs.string(value, "value")
            return Codecs.hex(Rc4.apply(k.toByteArray(Charsets.UTF_8), text.toByteArray(Charsets.UTF_8)))
        }

        /**
         * Decrypts the hex text [value] that `rc4Encode` wrote.
         *
         * Only lowercase hex digits count; an odd length throws. A wrong key gives garbage, not an error.
         *
         * @param key The key text.
         * @param value The cipher text as lowercase hex.
         * @return The plain text.
         * @throws IllegalArgumentException `"key must be between 1 and 256 bytes"` for a key of another size.
         */
        fun rc4Decode(key: Any?, value: Any?): String {
            val k = ScriptArgs.string(key, "key")
            val text = ScriptArgs.string(value, "value")
            return String(Rc4.apply(k.toByteArray(Charsets.UTF_8), Codecs.unhex(text)), Charsets.UTF_8)
        }

        /**
         * Encrypts [value] with a password, with the PBEWithMD5AndDES scheme of the Jasypt library, which other
         * Jasypt users can decrypt. The name is historical: it has nothing to do with the SEED cipher.
         *
         * The result differs on every call, as a random salt is part of it.
         *
         * @param seed The password; it may not be empty.
         * @param value The text to encrypt, as UTF-8.
         * @return The cipher text as Base64 in one line.
         * @throws IllegalArgumentException When [seed] is empty.
         */
        fun seedEncode(seed: Any?, value: Any?): String {
            val password = ScriptArgs.string(seed, "seed")
            val text = ScriptArgs.string(value, "value")
            return PbeMd5Des.encrypt(password, text)
        }

        /**
         * Decrypts the Base64 text [value] that `seedEncode` wrote.
         *
         * @param seed The password.
         * @param value The cipher text.
         * @return The plain text.
         * @throws IllegalStateException When [seed] isn't the password used to encrypt, or [value] isn't a cipher text.
         * @throws IllegalArgumentException When [seed] is empty.
         */
        fun seedDecode(seed: Any?, value: Any?): String {
            val password = ScriptArgs.string(seed, "seed")
            val text = ScriptArgs.string(value, "value")
            return PbeMd5Des.decrypt(password, text)
        }

        // endregion

        // region Hashes

        /**
         * The hash code of [value] as Java computes it for a string: `h = 31 * h + c` over its UTF-16 characters, as a
         * 32-bit integer. The same text always gives the same number, on every device.
         */
        fun hashCode(value: Any?): Int =
            ScriptArgs.string(value, "value").hashCode()

        /**
         * The MD2 hash of [value] as lowercase hex.
         *
         * @throws IllegalArgumentException When the device doesn't offer MD2.
         */
        fun md2(value: Any?): String = hash("MD2", value)

        /** The MD5 hash of [value] (as UTF-8) as lowercase hex. */
        fun md5(value: Any?): String = hash("MD5", value)

        /** The SHA-1 hash of [value] (as UTF-8) as lowercase hex. */
        fun sha(value: Any?): String = hash("SHA-1", value)

        /** The SHA-256 hash of [value] (as UTF-8) as lowercase hex. */
        fun sha256(value: Any?): String = hash("SHA-256", value)

        /** The SHA-384 hash of [value] (as UTF-8) as lowercase hex. */
        fun sha384(value: Any?): String = hash("SHA-384", value)

        /** The SHA-512 hash of [value] (as UTF-8) as lowercase hex. */
        fun sha512(value: Any?): String = hash("SHA-512", value)

        /**
         * The SHA3-224 hash of [value] (as UTF-8) as lowercase hex.
         *
         * @throws IllegalArgumentException When the device doesn't offer SHA-3.
         */
        fun sha3_224(value: Any?): String = hash("SHA3-224", value)

        /**
         * The SHA3-256 hash of [value] (as UTF-8) as lowercase hex.
         *
         * @throws IllegalArgumentException When the device doesn't offer SHA-3.
         */
        fun sha3_256(value: Any?): String = hash("SHA3-256", value)

        /**
         * The SHA3-384 hash of [value] (as UTF-8) as lowercase hex.
         *
         * @throws IllegalArgumentException When the device doesn't offer SHA-3.
         */
        fun sha3_384(value: Any?): String = hash("SHA3-384", value)

        /**
         * The SHA3-512 hash of [value] (as UTF-8) as lowercase hex.
         *
         * @throws IllegalArgumentException When the device doesn't offer SHA-3.
         */
        fun sha3_512(value: Any?): String = hash("SHA3-512", value)

        // endregion

        // region IDs

        /** A random version 4 UUID, 36 lowercase characters with hyphens. */
        fun uuid(): String = java.util.UUID.randomUUID().toString()

        /**
         * A version 7 UUID, 36 lowercase characters with hyphens: the current time in milliseconds in front, so
         * IDs made later sort after earlier ones (those made within the same millisecond in no set order).
         */
        fun uuidv7(): String =
            Ids.uuidV7(System.currentTimeMillis(), ByteArray(16).also(random::nextBytes))

        /**
         * A ULID, 26 uppercase characters: the current time in milliseconds in front, then random characters, so
         * IDs made later sort after earlier ones (those made within the same millisecond in no set order).
         */
        fun ulid(): String =
            Ids.ulid(System.currentTimeMillis(), ByteArray(10).also(random::nextBytes))

        // endregion

        private fun randomBytes(length: Int): String =
            Codecs.base64(ByteArray(length).also(random::nextBytes))

        private fun hash(algorithm: String, value: Any?): String {
            val text = ScriptArgs.string(value, "value")
            val digest = try {
                MessageDigest.getInstance(algorithm)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalArgumentException(e)
            }
            return Codecs.hex(digest.digest(text.toByteArray(Charsets.UTF_8)))
        }

        /** The UTF-8 bytes of [text] when [valid] accepts them; otherwise its Base64 decoding when that is accepted. */
        private fun bytesOrBase64(text: String, valid: (ByteArray) -> Boolean): ByteArray {
            val raw = text.toByteArray(Charsets.UTF_8)
            if (!valid(raw)) {
                try {
                    val decoded = Codecs.unbase64(text)
                    if (valid(decoded)) return decoded
                } catch (_: IllegalArgumentException) {
                }
            }
            return raw
        }

        private fun ariaOf(key: String): Aria128 =
            Aria128(MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8)))

        private fun des3Key(key: String): ByteArray {
            val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
            val out = digest.copyOf(24)
            for (i in 0 until 8) out[16 + i] = out[i]
            return out
        }

        /** Both ciphers of a DES key, made together so that a bad key fails before the data is read. */
        private fun desCiphers(key: String): Pair<Cipher, Cipher> {
            val bytes = Codecs.unbase64(key)
            val spec = SecretKeySpec(bytes, 0, bytes.size, "DES")
            val encrypt = Cipher.getInstance("DES")
            encrypt.init(Cipher.ENCRYPT_MODE, spec)
            val decrypt = Cipher.getInstance("DES")
            decrypt.init(Cipher.DECRYPT_MODE, spec)
            return encrypt to decrypt
        }
    }

    override val name: String = "Security"

    override val objects: List<ApiObject> = getApiObjects<Security>()

    override val instanceClass: Class<Security> = Security::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = Security(ScriptJson.current())
}
