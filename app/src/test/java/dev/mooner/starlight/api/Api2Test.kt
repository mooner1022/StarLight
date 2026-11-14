package dev.mooner.starlight.api

import dev.mooner.starlight.api.api2.EventApi
import dev.mooner.starlight.listener.event.Api2CommandEvent
import dev.mooner.starlight.listener.event.Api2MessageEvent
import dev.mooner.starlight.listener.event.Api2NotificationRemovedEvent
import dev.mooner.starlight.plugincore.language.DefaultJSCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

/** API2 as MessengerBot R has it: event names and the starting code; command parsing is in Api2CommandParseTest. */
class Api2Test {

    @Test
    fun `event names are MessengerBot R's`() {
        val event = EventApi.Event()
        assertEquals("message", event.MESSAGE)
        assertEquals("command", event.COMMAND)
        assertEquals("notificationPosted", event.NOTIFICATION_POSTED)
        assertEquals("notificationRemoved", event.NOTIFICATION_REMOVED)
        assertEquals("startCompile", event.START_COMPILE)
        assertEquals("activityCreate", event.Activity.CREATE)
        assertEquals("activityBackPressed", event.Activity.BACK_PRESSED)
    }

    @Test
    fun `new projects start with listeners added to the bot, not exported functions`() {
        val generator = DefaultJSCodeGenerator()
        val message = Api2MessageEvent().generateCode(generator)
        assertEquals(listOf("const bot = BotManager.getCurrentBot();"), message.imports)
        assertEquals("function onMessage(msg) {\n}\nbot.addListener(Event.MESSAGE, onMessage);", message.function)

        val command = Api2CommandEvent().generateCode(generator)
        assertEquals("function onCommand(msg) {\n}\nbot.setCommandPrefix(\"@\");\nbot.addListener(Event.COMMAND, onCommand);",
            command.function)

        // Beside StarLight's onMessage, the listener is named apart, so Rhino doesn't call it as that event too.
        assertEquals("function onApi2Message(msg) {\n}\nbot.addListener(Event.MESSAGE, onApi2Message);",
            Api2MessageEvent().let { it.generateCode(generator, it.alternateFunctionName) }.function)

        val removed = Api2NotificationRemovedEvent().generateCode(generator)
        assertEquals("function onNotificationRemoved(sbn, rankingMap, reason) {\n}\nbot.addListener(Event.NOTIFICATION_REMOVED, onNotificationRemoved);",
            removed.function)
    }
}
