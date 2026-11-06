package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.tools.TextDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The diffs the chat shows for what a tool changed. */
class TextDiffTest {

    private fun numbered(count: Int, change: Map<Int, String> = emptyMap()) =
        (1..count).joinToString("\n") { change[it] ?: "line $it" }

    @Test
    fun nothingChangedIsNoDiff() {
        assertNull(TextDiff.unified("a\nb\n", "a\nb\n"))
        // A CRLF file rewritten with LF is the same text.
        assertNull(TextDiff.unified("a\r\nb\r\n", "a\nb\n"))
    }

    @Test
    fun aChangedLineShowsWithItsContext() {
        val diff = TextDiff.unified(numbered(10), numbered(10, mapOf(5 to "five")))
        assertEquals(
            listOf("@@ -3,5 +3,5 @@", " line 3", " line 4", "-line 5", "+five", " line 6", " line 7"),
            diff!!.lines(),
        )
    }

    @Test
    fun aNewFileIsAllAdded() {
        assertEquals(listOf("@@ -1,0 +1,2 @@", "+first", "+second"), TextDiff.unified("", "first\nsecond")!!.lines())
        assertEquals(listOf("@@ -1,1 +1,0 @@", "-gone"), TextDiff.unified("gone", "")!!.lines())
    }

    @Test
    fun farApartChangesGetHunksOfTheirOwn() {
        val diff = TextDiff.unified(numbered(30), numbered(30, mapOf(3 to "three", 25 to "twenty-five")))!!
        val headers = diff.lines().filter { it.startsWith("@@") }
        assertEquals(listOf("@@ -1,5 +1,5 @@", "@@ -23,5 +23,5 @@"), headers)
        // Close ones share one.
        val close = TextDiff.unified(numbered(30), numbered(30, mapOf(10 to "ten", 13 to "thirteen")))!!
        assertEquals(1, close.lines().count { it.startsWith("@@") })
    }

    @Test
    fun insertedLinesKeepTheRestAligned() {
        val diff = TextDiff.unified("a\nb\nc\nd", "a\nb\nnew\nc\nd")!!
        assertEquals(listOf("@@ -1,4 +1,5 @@", " a", " b", "+new", " c", " d"), diff.lines())
    }

    @Test
    fun aFinalLineBreakIsNoExtraLine() {
        assertEquals(listOf("@@ -1,2 +1,2 @@", " a", "-b", "+c"), TextDiff.unified("a\nb\n", "a\nc\n")!!.lines())
    }

    @Test
    fun aLongDiffIsCut() {
        val old = (1..2000).joinToString("\n") { "old $it" }
        val new = (1..2000).joinToString("\n") { "new $it" }
        val diff = TextDiff.unified(old, new, maxChars = 500)!!
        assertTrue(diff.length < 700)
        assertTrue(diff.lines().last().startsWith("… "))
    }
}
