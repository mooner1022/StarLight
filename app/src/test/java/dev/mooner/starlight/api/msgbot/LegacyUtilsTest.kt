package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.legacy.UtilsSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** The text helpers of legacy `Utils`. */
class LegacyUtilsTest {

    @Test
    fun `random strings have the length asked and only letters and digits`() {
        val text = UtilsSupport.randomAlphanumeric(200, Random(1))
        assertEquals(200, text.length)
        assertTrue(text.all { it.isLetterOrDigit() && it.code < 128 })
    }

    @Test
    fun `zero or less gives an empty string`() {
        assertEquals("", UtilsSupport.randomAlphanumeric(0))
        assertEquals("", UtilsSupport.randomAlphanumeric(-3))
    }

    @Test
    fun `removeRLO drops every override character and nothing else`() {
        assertEquals("abcd", UtilsSupport.removeRLO("a‮b‮c‮d"))
        assertEquals("a‭b", UtilsSupport.removeRLO("a‭b"))
    }

    @Test
    fun `the family is picked by weight and the rest falls to Chrome`() {
        assertTrue("Trident" in UtilsSupport.userAgent(5.0, 0.0) || "MSIE" in UtilsSupport.userAgent(5.0, 0.99))
        assertTrue("Firefox" in UtilsSupport.userAgent(30.0, 0.0))
        assertTrue("Chrome" in UtilsSupport.userAgent(60.0, 0.0))
        assertTrue("Chrome" in UtilsSupport.userAgent(99.9, 0.0))
        assertTrue(UtilsSupport.userAgent(98.0, 0.5).contains("Opera") || UtilsSupport.userAgent(98.0, 0.5).contains("OPR"))
    }

    @Test
    fun `a random user agent is never empty`() {
        repeat(100) { assertFalse(UtilsSupport.randomUserAgent().isBlank()) }
    }

    @Test
    fun `xml becomes objects with localname, attributes and content`() {
        val json = UtilsSupport.xmlToJson("<root id=\"1\"><item>a</item>\n  <empty/></root>")
        assertTrue(json.contains("\"localname\": \"root\""))
        assertTrue(json.contains("\"attributes\""))
        assertTrue(json.contains("\"id\": \"1\""))
        assertTrue(json.contains("\"localname\": \"item\""))
        assertTrue(json.contains("\"a\""))
        assertTrue(json.contains("    \"localname\""))
    }

    @Test
    fun `an element without attributes or children has only its name`() {
        assertEquals("{\n    \"localname\": \"a\"\n}", UtilsSupport.xmlToJson("<a/>"))
    }

    @Test
    fun `a namespace is kept and its declaration is not an attribute`() {
        val json = UtilsSupport.xmlToJson("<a xmlns=\"urn:x\"/>")
        assertTrue(json.contains("\"namespace\": \"urn:x\""))
        assertFalse(json.contains("attributes"))
    }

    @Test
    fun `broken xml is rejected`() {
        try {
            UtilsSupport.xmlToJson("<a><b></a>")
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }
}
