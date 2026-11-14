package dev.mooner.starlight.ui.logs

import dev.mooner.starlight.plugincore.logger.LogType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {

    @Test
    fun anEmptyFilterShowsEverything() {
        val filter = LogFilter()

        assertFalse(filter.isActive)
        assertTrue(filter.matches(LogType.DEBUG, null, "anything"))
    }

    @Test
    fun aWordFindsTheMessagesItIsIn() {
        val filter = LogFilter(message = LogFilter.messagePattern("error"))

        assertTrue(filter.matches(LogType.ERROR, "bot", "TypeError: x is not a function"))
        assertTrue(filter.matches(LogType.INFO, "bot", "first line\nan ERROR on the second"))
        assertFalse(filter.matches(LogType.INFO, "bot", "all good"))
    }

    @Test
    fun aPatternThatCoveredTheWholeMessageStillMatches() {
        // What had to be written before to find a word.
        val filter = LogFilter(message = LogFilter.messagePattern(".*timeout.*"))

        assertTrue(filter.matches(LogType.WARN, null, "request timeout after 3s"))
    }

    @Test
    fun aTagIsFoundByPartOfItWhateverItsCase() {
        val filter = LogFilter(tags = listOf("project-", "listener"))

        assertTrue(filter.matches(LogType.INFO, "Project-hello", "x"))
        assertTrue(filter.matches(LogType.INFO, "NotificationListener", "x"))
        assertFalse(filter.matches(LogType.INFO, "System", "x"))
        assertFalse(filter.matches(LogType.INFO, null, "x"))
    }

    @Test
    fun everyConditionSetHasToHold() {
        val filter = LogFilter(listOf(LogType.ERROR), listOf("bot"), LogFilter.messagePattern("boom"))

        assertEquals(3, filter.conditions)
        assertTrue(filter.matches(LogType.ERROR, "bot", "boom"))
        assertFalse(filter.matches(LogType.WARN, "bot", "boom"))
        assertFalse(filter.matches(LogType.ERROR, "other", "boom"))
        assertFalse(filter.matches(LogType.ERROR, "bot", "fine"))
    }
}
