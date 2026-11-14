package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.api2.Api2Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Api2CommandParseTest {

    private fun parse(content: String, prefix: String) = Api2Command.parse(content, prefix)

    @Test
    fun `the first word minus the prefix is the command and the rest are arguments`() {
        assertEquals("add" to listOf("4", "7"), parse("@add 4 7", "@"))
    }

    @Test
    fun `the split is at single spaces and keeps empty words`() {
        assertEquals("a" to listOf("", "b"), parse("!a  b", "!"))
        assertEquals("a" to listOf(""), parse("!a ", "!"))
        assertEquals("a" to listOf("", ""), parse("!a  ", "!"))
    }

    @Test
    fun `newlines and tabs do not separate words`() {
        assertEquals("a\nb" to listOf("c"), parse("!a\nb c", "!"))
    }

    @Test
    fun `the prefix alone is a command with no name`() {
        assertEquals("" to emptyList<String>(), parse("!", "!"))
        assertEquals("" to listOf("foo"), parse("! foo", "!"))
    }

    @Test
    fun `only one copy of the prefix is removed`() {
        assertEquals("!x" to emptyList<String>(), parse("!!x", "!"))
    }

    @Test
    fun `a first word that doesn't start with the prefix is no command`() {
        assertNull(parse("hello", "@"))
        assertNull(parse("help@", "@"))
        assertNull(parse("say @x", "@"))
        assertNull(parse("@X", "@x"))
    }

    @Test
    fun `an empty prefix makes every message a command`() {
        assertEquals("hello" to listOf("world"), parse("hello world", ""))
        assertEquals("" to emptyList<String>(), parse("", ""))
        assertEquals("" to listOf("x"), parse(" x", ""))
    }
}
