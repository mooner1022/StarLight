package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.msgbot.crypto.Aria128
import dev.mooner.starlight.api.msgbot.crypto.Base32
import dev.mooner.starlight.api.msgbot.crypto.Codecs
import dev.mooner.starlight.api.msgbot.crypto.Ids
import dev.mooner.starlight.api.msgbot.crypto.PbeMd5Des
import dev.mooner.starlight.api.msgbot.crypto.Rc4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec

/** The pure parts of `Security`: ciphers, encodings and ID layouts. */
class SecurityApiTest {

    private fun bytes(hex: String): ByteArray = Codecs.unhex(hex)

    @Test
    fun `ARIA-128 matches the RFC 5794 vector`() {
        val aria = Aria128(bytes("000102030405060708090a0b0c0d0e0f"))
        val plain = bytes("00112233445566778899aabbccddeeff")
        val out = ByteArray(16)
        aria.encryptBlock(plain, 0, out, 0)
        assertEquals("d718fbd6ab644c739da95f3be6451778", Codecs.hex(out))
        val back = ByteArray(16)
        aria.decryptBlock(out, 0, back, 0)
        assertArrayEquals(plain, back)
    }

    @Test
    fun `ARIA ECB pads with PKCS7 and round trips`() {
        val aria = Aria128(bytes("000102030405060708090a0b0c0d0e0f"))
        assertEquals(16, aria.encryptEcb(ByteArray(0)).size)
        assertEquals(32, aria.encryptEcb(ByteArray(16)).size)
        assertEquals(16, aria.encryptEcb(ByteArray(15)).size)
        for (n in 0..40) {
            val data = ByteArray(n) { (it * 7).toByte() }
            assertArrayEquals(data, aria.decryptEcb(aria.encryptEcb(data)))
        }
    }

    @Test
    fun `ARIA decrypt gives null for bad padding and throws for bad sizes`() {
        val aria = Aria128(bytes("000102030405060708090a0b0c0d0e0f"))
        val other = Aria128(bytes("0f0e0d0c0b0a09080706050403020100"))
        val cipher = aria.encryptEcb("hello".toByteArray())
        assertNull(other.decryptEcb(cipher))
        assertThrows(IllegalArgumentException::class.java) { aria.decryptEcb(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { aria.decryptEcb(ByteArray(15)) }
    }

    @Test
    fun `RC4 matches the published vectors`() {
        assertEquals("bbf316e8d940af0ad3", Codecs.hex(Rc4.apply("Key".toByteArray(), "Plaintext".toByteArray())))
        assertEquals("1021bf0420", Codecs.hex(Rc4.apply("Wiki".toByteArray(), "pedia".toByteArray())))
        assertThrows(IllegalArgumentException::class.java) { Rc4.apply(ByteArray(0), ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { Rc4.apply(ByteArray(257), ByteArray(1)) }
    }

    @Test
    fun `Base32 matches the RFC 4648 vectors`() {
        val cases = listOf(
            "" to "", "f" to "MY======", "fo" to "MZXQ====", "foo" to "MZXW6===",
            "foob" to "MZXW6YQ=", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI======",
        )
        for ((plain, encoded) in cases) {
            assertEquals(encoded, Base32.encode(plain.toByteArray()))
            assertEquals(plain, String(Base32.decode(encoded)))
        }
    }

    @Test
    fun `Base32 decoding is lenient`() {
        assertEquals("foobar", String(Base32.decode("mzxw 6ytb\noi")))
        assertEquals("foo", String(Base32.decode("MZXW6")))
        assertEquals("fo", String(Base32.decode("MZXQ====MZXW6")))
        assertEquals("f", String(Base32.decode("M!Y")))
        assertEquals("", String(Base32.decode("1890")))
    }

    @Test
    fun `hex is written lowercase and read lowercase only`() {
        assertEquals("00ff10ab", Codecs.hex(byteArrayOf(0, -1, 0x10, 0xab.toByte())))
        assertArrayEquals(byteArrayOf(0x1f, 0xab.toByte()), Codecs.unhex("1fab"))
        assertArrayEquals(byteArrayOf(-1), Codecs.unhex("1A"))
        assertArrayEquals(byteArrayOf(0xF5.toByte()), Codecs.unhex("A5"))
        assertEquals(0, Codecs.unhex("").size)
        assertThrows(StringIndexOutOfBoundsException::class.java) { Codecs.unhex("abc") }
    }

    @Test
    fun `Base64 wraps at 76 characters and ends with a line break`() {
        assertEquals("YQ==\n", Codecs.base64("a".toByteArray()))
        assertEquals("", Codecs.base64(ByteArray(0)))
        val long = Codecs.base64(ByteArray(57) { 'a'.code.toByte() })
        assertEquals(77, long.length)
        assertEquals('\n', long[76])
        val longer = Codecs.base64(ByteArray(58))
        val lines = longer.split('\n')
        assertEquals(76, lines[0].length)
        assertEquals(3, lines.size)
        assertEquals("", lines[2])
        assertTrue(lines[1].isNotEmpty())
    }

    @Test
    fun `hashes are lowercase hex of the UTF-8 bytes`() {
        fun hash(algorithm: String, text: String) = Codecs.hex(MessageDigest.getInstance(algorithm).digest(text.toByteArray()))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hash("MD5", "abc"))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hash("SHA-1", "abc"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash("SHA-256", "abc"))
    }

    @Test
    fun `password encryption reads what the JDK's PBEWithMD5AndDES writes`() {
        val salt = ByteArray(8) { (it + 1).toByte() }
        val password = "p@ssw0rd"
        val ours = PbeMd5Des.encrypt(password, "안녕 hello", salt)

        val factory = SecretKeyFactory.getInstance("PBEWithMD5AndDES")
        val key = factory.generateSecret(PBEKeySpec(password.toCharArray()))
        val jdk = Cipher.getInstance("PBEWithMD5AndDES")
        jdk.init(Cipher.ENCRYPT_MODE, key, PBEParameterSpec(salt, 1000))
        val expected = java.util.Base64.getEncoder().encodeToString(salt + jdk.doFinal("안녕 hello".toByteArray()))
        assertEquals(expected, ours)

        assertEquals("안녕 hello", PbeMd5Des.decrypt(password, ours))
        assertEquals("안녕 hello", PbeMd5Des.decrypt(password, PbeMd5Des.encrypt(password, "안녕 hello")))
    }

    @Test
    fun `password encryption fails on a wrong password or an empty one`() {
        val encrypted = PbeMd5Des.encrypt("right", "secret")
        assertThrows(IllegalStateException::class.java) { PbeMd5Des.decrypt("wrong", encrypted) }
        assertThrows(IllegalStateException::class.java) { PbeMd5Des.decrypt("right", "AAAA") }
        assertThrows(IllegalArgumentException::class.java) { PbeMd5Des.encrypt("", "x") }
        assertThrows(IllegalArgumentException::class.java) { PbeMd5Des.decrypt("", encrypted) }
    }

    @Test
    fun `ulid has 26 Crockford characters with the time in front`() {
        val zero = Ids.ulid(0L, ByteArray(10))
        assertEquals("0".repeat(26), zero)
        val id = Ids.ulid(1_700_000_000_000L, ByteArray(10) { 0xFF.toByte() })
        assertEquals(26, id.length)
        assertEquals("01HF7YAT00", id.substring(0, 10))
        assertEquals("Z".repeat(16), id.substring(10))
        assertTrue(id.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
        assertThrows(IllegalArgumentException::class.java) { Ids.ulid(1L shl 48, ByteArray(10)) }
        assertThrows(IllegalArgumentException::class.java) { Ids.ulid(-1L, ByteArray(10)) }
    }

    @Test
    fun `uuidv7 has the time, version and variant bits`() {
        val id = Ids.uuidV7(0x0123456789ABL, ByteArray(16) { 0xFF.toByte() })
        assertEquals("01234567-89ab-7fff-bfff-ffffffffffff", id)
        val zero = Ids.uuidV7(0L, ByteArray(16))
        assertEquals("00000000-0000-7000-8000-000000000000", zero)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            .matches(Ids.uuidV7(System.currentTimeMillis(), ByteArray(16) { it.toByte() })))
    }
}
