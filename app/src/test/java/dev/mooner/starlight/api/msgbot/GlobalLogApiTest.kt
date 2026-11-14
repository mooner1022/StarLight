package dev.mooner.starlight.api.msgbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mozilla.javascript.Undefined

class GlobalLogApiTest {

    @Test
    fun oneArgumentTakesTheDefaultToast() {
        val quiet = LogArgs.read("hi", null, hasSecond = false, defaultToast = false)
        assertNull(quiet.tag)
        assertEquals("hi", quiet.message)
        assertFalse(quiet.showToast)

        assertTrue(LogArgs.read("hi", null, hasSecond = false, defaultToast = true).showToast)
    }

    @Test
    fun aBooleanSecondArgumentIsTheToastSwitch() {
        val on = LogArgs.read("saved", true, hasSecond = true, defaultToast = false)
        assertEquals("saved", on.message)
        assertNull(on.tag)
        assertTrue(on.showToast)

        assertFalse(LogArgs.read("failed", false, hasSecond = true, defaultToast = true).showToast)
    }

    @Test
    fun aNumberCountsAsABooleanAndNullKeepsTheDefault() {
        assertTrue(LogArgs.read("x", 1.0, hasSecond = true, defaultToast = false).showToast)
        assertFalse(LogArgs.read("x", 0.0, hasSecond = true, defaultToast = true).showToast)
        assertTrue(LogArgs.read("x", null, hasSecond = true, defaultToast = true).showToast)
        assertTrue(LogArgs.read("x", Undefined.instance, hasSecond = true, defaultToast = true).showToast)
    }

    @Test
    fun aTextSecondArgumentIsTheMessageUnderTheFirstAsItsTag() {
        val request = LogArgs.read("net", "connected", hasSecond = true, defaultToast = true)
        assertEquals("net", request.tag)
        assertEquals("connected", request.message)
        // The tag form never toasts, even for an error.
        assertFalse(request.showToast)
    }

    @Test
    fun theFirstArgumentIsWrittenAsText() {
        assertEquals("null", LogArgs.read(null, null, hasSecond = false, defaultToast = false).message)
        assertEquals("5", LogArgs.read(5.0, null, hasSecond = false, defaultToast = false).message)
        assertEquals("[1, 2]", LogArgs.read(listOf(1.0, 2.0), null, hasSecond = false, defaultToast = false).message)
    }

    @Test
    fun anObjectAsTheSecondArgumentIsRefused() {
        try {
            LogArgs.read("x", listOf(1), hasSecond = true, defaultToast = false)
            fail("expected an error")
        } catch (e: IllegalArgumentException) {
            assertEquals("showToast must be a boolean", e.message)
        }
    }

    @Test
    fun globalsReadDelaysAsMessengerBotDoes() {
        assertEquals(100, GlobalArgs.convertToInt(100.0))
        assertEquals(1, GlobalArgs.convertToInt(1.9))
        assertEquals(100, GlobalArgs.convertToInt("100"))
        assertEquals(0, GlobalArgs.convertToInt("1.5"))
        assertEquals(0, GlobalArgs.convertToInt(" 5"))
        assertEquals(1, GlobalArgs.convertToInt(true))
        assertEquals(0, GlobalArgs.convertToInt(false))
        assertEquals(0, GlobalArgs.convertToInt(null))
        assertEquals(0, GlobalArgs.convertToInt(Undefined.instance))
        assertEquals(0, GlobalArgs.convertToInt(Double.NaN))
        assertEquals(0, GlobalArgs.convertToInt(listOf(1)))
    }
}
