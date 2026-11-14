package dev.mooner.starlight.languages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryPointsTest {

    private val expected = listOf("onMessage", "response", "onNotificationPosted")

    @Test
    fun aScriptWithAFunctionOfAnEventHasNothingToBeTold() {
        assertNull(EntryPoints.notice(expected, has = { it == "onMessage" }, listeners = 0, source = "export function onMessage(e) {}"))
    }

    @Test
    fun aFunctionThatIsNotExportedIsNamedAsTheMistakeItIs() {
        val source = "// bot\nfunction onMessage(event) {\n    event.room.send(\"hi\");\n}\n"

        val notice = EntryPoints.notice(expected, has = { false }, listeners = 0, source = source)!!

        assertTrue(notice.isMistake)
        assertTrue(notice.text.contains("'onMessage' is defined but not exported"))
        assertTrue(notice.text.contains("export function onMessage"))
    }

    @Test
    fun aListenerCountsThoughTheFunctionBehindItIsNotExported() {
        // The starting code of an API2 project: the function is handed to bot.addListener, not exported.
        val source = "const bot = BotManager.getCurrentBot();\nfunction onMessage(msg) {}\nbot.addListener(Event.MESSAGE, onMessage);\n"

        assertNull(EntryPoints.notice(expected, has = { false }, listeners = 1, source = source))
    }

    @Test
    fun aProjectThatTakesNoEventsIsToldWithoutBeingCalledAMistake() {
        val notice = EntryPoints.notice(expected, has = { false }, listeners = 0, source = "export function helper() {}")!!

        assertEquals(false, notice.isMistake)
        assertTrue(notice.text.contains("onMessage, response, onNotificationPosted"))
    }

    @Test
    fun aLanguageThatReachesEveryFunctionIsNotAskedAboutExports() {
        // Rhino: the function would be found if it were there, so its absence is all there is to say.
        val notice = EntryPoints.notice(expected, has = { false }, listeners = 0, source = null)!!

        assertEquals(false, notice.isMistake)
    }

    @Test
    fun aProjectThatReceivesNoEventIsLeftAlone() {
        assertNull(EntryPoints.notice(emptyList(), has = { false }, listeners = 0, source = "function onMessage() {}"))
    }
}
