package dev.mooner.starlight.api.msgbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import java.math.BigInteger

/** The argument conversions the MessengerBot-compatible objects share, with values as both engines pass them. */
class ScriptArgsTest {

    @Test
    fun `null and undefined are written as null`() {
        assertEquals("null", ScriptArgs.stringify(null))
        assertEquals("null", ScriptArgs.stringify(Undefined.instance))
    }

    @Test
    fun `whole numbers have no fraction, whichever engine passed them`() {
        assertEquals("5", ScriptArgs.stringify(5))
        assertEquals("5", ScriptArgs.stringify(5.0))
        assertEquals("1.5", ScriptArgs.stringify(1.5))
        assertEquals("-3", ScriptArgs.stringify(-3L))
    }

    @Test
    fun `numbers past exact integers are written as Java writes a double`() {
        assertEquals("1.0E21", ScriptArgs.stringify(1e21))
        assertEquals("NaN", ScriptArgs.stringify(Double.NaN))
    }

    @Test
    fun `arrays are their items, joined`() {
        assertEquals("[a, 1, null]", ScriptArgs.stringify(listOf("a", 1.0, null)))
    }

    @Test
    fun `a string parameter refuses null and undefined only`() {
        assertEquals("12", ScriptArgs.string(12, "path"))
        val e = assertThrows(IllegalArgumentException::class.java) { ScriptArgs.string(Undefined.instance, "path") }
        assertEquals("path must be a string", e.message)
        assertNull(ScriptArgs.stringOrNull(null))
    }

    @Test
    fun `booleans take numbers, the two exact strings, and a default`() {
        assertTrue(ScriptArgs.boolean(1, "b", false))
        assertFalse(ScriptArgs.boolean(0.0, "b", true))
        assertTrue(ScriptArgs.boolean("true", "b", false))
        assertTrue(ScriptArgs.boolean("yes", "b", true))
        assertFalse(ScriptArgs.boolean("TRUE", "b", false))
        assertTrue(ScriptArgs.boolean(null, "b", true))
        val e = assertThrows(IllegalArgumentException::class.java) { ScriptArgs.boolean(mapOf("a" to 1), "hideToast", false) }
        assertEquals("hideToast must be a boolean", e.message)
    }

    @Test
    fun `numbers are cut to whole ones, and strings must be whole for an int`() {
        assertEquals(1, ScriptArgs.int(1.9, "n"))
        assertEquals(12, ScriptArgs.int("12", "n"))
        assertEquals(7, ScriptArgs.int("1.5", "n", 7))
        assertEquals(0, ScriptArgs.int(Double.NaN, "n"))
        assertEquals(1.5f, ScriptArgs.float("1.5", "n"))
        val e = assertThrows(IllegalArgumentException::class.java) { ScriptArgs.long(null, "delay") }
        assertEquals("delay must be a number", e.message)
        assertEquals(3L, ScriptArgs.long(Undefined.instance, "delay", 3L))
    }

    @Test
    fun `ids are whole numbers and BigInts, never strings`() {
        assertEquals(BigInteger.valueOf(18), ScriptArgs.integral(18))
        assertEquals(BigInteger.valueOf(18), ScriptArgs.integral(18.0))
        assertEquals(BigInteger("18446744073709551615"), ScriptArgs.integral(BigInteger("18446744073709551615")))
        assertNull(ScriptArgs.integral(18.5))
        assertNull(ScriptArgs.integral("18"))
    }

    @Test
    fun `nullish covers both engines' forms`() {
        assertTrue(ScriptArgs.isNullish(null))
        assertTrue(ScriptArgs.isNullish(Undefined.instance))
        assertFalse(ScriptArgs.isNullish(""))
    }

    @Test
    fun `Rhino's undefined is missing only where text can't go`() {
        // Rhino passes undefined as the JVM's one interned "undefined"; a string a script builds is another instance.
        val built = String("undefined".toCharArray())
        Context.enter()
        try {
            assertFalse(ScriptArgs.isNullish("undefined"))
            assertEquals("undefined", ScriptArgs.stringify("undefined"))
            assertTrue(ScriptArgs.isMissing("undefined"))
            assertTrue(ScriptArgs.isUndefined("undefined"))
            assertFalse(ScriptArgs.isMissing(built))
            assertTrue(ScriptArgs.boolean("undefined", "b", true))
        } finally {
            Context.exit()
        }
        // Not from Rhino: NeonJS passes undefined as null, so its "undefined" is text.
        assertFalse(ScriptArgs.isMissing("undefined"))
    }
}
