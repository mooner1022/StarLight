package dev.mooner.starlight.media.script

import dev.mooner.starlight.media.send.LeaveTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Options as Rhino hands them over: JS objects are Maps, numbers are Doubles. */
class SendOptionsTest {

    @Test
    fun objects() {
        val options = SendOptions.read(mapOf("fileName" to "a", "timeout" to 2500.0, "cache" to true, "headers" to mapOf("A" to "b")))
        assertEquals(SendOptions("a", 2500, true, mapOf("A" to "b")), options)
    }

    @Test
    fun absentAndTimeouts() {
        assertEquals(SendOptions(), SendOptions.read(null))
        assertEquals(1000, SendOptions.read(mapOf("timeout" to 10.0)).timeoutMillis)
        assertEquals(600_000, SendOptions.read(mapOf("timeout" to 1e9)).timeoutMillis)
        assertNull(SendOptions.read(mapOf("timeout" to 0.0)).timeoutMillis)
    }

    @Test
    fun leave() {
        assertNull(SendOptions.read(mapOf("cache" to true)).leaveTo)
        assertEquals(LeaveTo.Home, SendOptions.read(mapOf("leave" to true)).leaveTo)
        assertEquals(LeaveTo.Stay, SendOptions.read(mapOf("leave" to false)).leaveTo)
        assertEquals(LeaveTo.Home, SendOptions.read(mapOf("leave" to "home")).leaveTo)
        assertEquals(LeaveTo.StarLight, SendOptions.read(mapOf("leave" to "StarLight")).leaveTo)
        assertEquals(LeaveTo.Stay, SendOptions.read(mapOf("leave" to "stay")).leaveTo)
        assertEquals(LeaveTo.App("com.example.app"), SendOptions.read(mapOf("leave" to " com.example.app ")).leaveTo)
    }

    @Test
    fun `the saved choice goes home unless stay or StarLight was saved`() {
        assertEquals(LeaveTo.Home, LeaveTo.ofSetting(null))
        assertEquals(LeaveTo.Home, LeaveTo.ofSetting("home"))
        assertEquals(LeaveTo.Stay, LeaveTo.ofSetting("stay"))
        assertEquals(LeaveTo.StarLight, LeaveTo.ofSetting("starlight"))
    }

    @Test
    fun mistakesAreReported() {
        assertTrue(failure { SendOptions.read(15000.0) }.contains("must be an object"))
        assertTrue(failure { SendOptions.read(mapOf("filename" to "a")) }.contains("Unknown option 'filename'"))
        assertTrue(failure { SendOptions.read(mapOf("leave" to 3.0)) }.contains("leave must be"))
    }

    private fun failure(block: () -> Unit): String {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}
