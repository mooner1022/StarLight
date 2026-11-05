package dev.mooner.starlight.api

import dev.mooner.starlight.api.api2.Api2Command
import dev.mooner.starlight.api.api2.EventApi
import dev.mooner.starlight.listener.event.Api2CommandEvent
import dev.mooner.starlight.listener.event.Api2MessageEvent
import dev.mooner.starlight.listener.event.Api2NotificationRemovedEvent
import dev.mooner.starlight.plugincore.language.DefaultJSCodeGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** API2 as MessengerBot R documents it: commands, event names, and the starting code. */
class Api2Test {

    @Test
    fun `a command is the word after the prefix, and its arguments the words after that`() {
        val (command, args) = Api2Command.parse("@add 4 7", "@")!!
        assertEquals("add", command)
        assertArrayEquals(arrayOf("4", "7"), args)
    }

    @Test
    fun `extra spaces don't make empty arguments`() {
        val (command, args) = Api2Command.parse("/angles  30   60 90 ", "/")!!
        assertEquals("angles", command)
        assertArrayEquals(arrayOf("30", "60", "90"), args)
    }

    @Test
    fun `a longer prefix works the same`() {
        val (command, args) = Api2Command.parse("!!help", "!!")!!
        assertEquals("help", command)
        assertArrayEquals(emptyArray<String>(), args)
    }

    @Test
    fun `no prefix, another start or a prefix alone isn't a command`() {
        assertNull(Api2Command.parse("@help", null))
        assertNull(Api2Command.parse("hello", "@"))
        assertNull(Api2Command.parse("help@", "@"))
        assertNull(Api2Command.parse("@", "@"))
        assertNull(Api2Command.parse("@   ", "@"))
    }

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

        val removed = Api2NotificationRemovedEvent().generateCode(generator)
        assertEquals("function onNotificationRemoved(sbn, rankingMap, reason) {\n}\nbot.addListener(Event.NOTIFICATION_REMOVED, onNotificationRemoved);",
            removed.function)
    }
}
