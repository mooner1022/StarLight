package dev.mooner.starlight.plugincore.project

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScriptErrorTextTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** As NeonJS's exception is: where it happened is in properties, not in its text. */
    @Suppress("unused")
    private class PlacedError(
        message: String,
        val line: Int,
        val column: Int,
        val sourceName: String?,
        val jsStack: String? = null,
    ) : RuntimeException(message) {
        override fun toString(): String = "PlacedError: $message"
    }

    @Test
    fun thePlaceIsOnTheFirstLineByThePathInsideTheProject() {
        val project = tmp.newFolder("bot")
        val error = PlacedError("TypeError: x is not a function", 12, 5, File(project, "lib/util.js").canonicalPath)

        assertEquals("PlacedError: TypeError: x is not a function (lib/util.js:12:5)", ScriptErrorText.of(error, project))
    }

    @Test
    fun theScriptsStackFollowsCutToAFewLines() {
        val stack = (1..30).joinToString("\n") { "    at f$it (main.js:$it:1)" }
        val text = ScriptErrorText.of(PlacedError("Error: boom", 3, 0, "main.js", stack))

        val lines = text.lines()
        assertEquals("PlacedError: Error: boom (main.js:3)", lines.first())
        assertEquals(11, lines.size)
        assertEquals("    at f10 (main.js:10:1)", lines.last())
    }

    @Test
    fun aTextThatAlreadyNamesTheLineIsLeftAsItIs() {
        // Rhino's way: "(name#line)" is part of the message.
        val error = PlacedError("ReferenceError: \"x\" is not defined. (bot#12)", 12, 0, "bot")

        assertEquals("PlacedError: ReferenceError: \"x\" is not defined. (bot#12)", ScriptErrorText.of(error))
    }

    @Test
    fun thePlaceOfTheCauseIsFound() {
        val cause = PlacedError("SyntaxError: unexpected token", 7, 2, "main.js")
        val error = IllegalStateException("compile failed", cause)

        assertEquals("java.lang.IllegalStateException: compile failed (main.js:7:2)", ScriptErrorText.of(error))
    }

    @Test
    fun anErrorWithNoPlaceReadsAsBefore() {
        assertEquals("java.lang.IllegalStateException: no place", ScriptErrorText.of(IllegalStateException("no place")))
    }
}
