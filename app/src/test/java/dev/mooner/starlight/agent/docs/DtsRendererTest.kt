package dev.mooner.starlight.agent.docs

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the agent reads for the app's main APIs and events (docs/agent-docs.md §11). Checks the declarations that
 * matter rather than whole texts, so that docs can be reworded; every rendering is also written to
 * `build/agent-docs-preview/` for review.
 */
class DtsRendererTest {

    private val catalog = TestDocs.catalog
    private val renderer = TestDocs.renderer

    private fun api(name: String): String =
        renderer.describeApi(catalog.api(name)!!).also { TestDocs.preview("api-$name", it) }

    private fun event(id: String): String =
        renderer.describeEvent(catalog.event(id)!!).also { TestDocs.preview("event-$id", it) }

    private fun assertHas(text: String, vararg lines: String) {
        for (line in lines)
            assertTrue("missing `$line` in:\n$text", text.contains(line))
    }

    @Test
    fun `BotManager is a global with optional parameters from overloads`() {
        assertHas(api("BotManager"),
            "declare const BotManager: {",
            "getCurrentBot(): Bot;",
            "getRooms(packageName?: string | null): JavaList<Api2Room>;",
            "interface Bot {",
            "interface JavaList<T>",
            // A type alias is rendered as the function type it stands for.
            "listener: (p1: any, p2: any, p3: any) => void",
            // @listeners: each listener event's name paired with its listener's arguments.
            "on(eventName: \"message\", listener: (msg: Api2Message) => void): void;",
            "addListener(eventName: \"command\", listener: (msg: Api2Command) => void): void;",
        )
    }

    @Test
    fun `Event's names are literal types, so bot_on(Event_MESSAGE) picks the message overload`() {
        assertHas(api("Event"),
            "readonly MESSAGE: \"message\";",
            "readonly START_COMPILE: \"startCompile\";",
            "readonly CREATE: \"activityCreate\";",
        )
    }

    @Test
    fun `MediaSharer and the message event`() {
        assertHas(api("MediaSharer"), "declare const MediaSharer: {")
        assertHas(event("starlight.message.create"),
            "// Received with: onMessage(event)",
            "declare function onMessage(event: Message): void;",
            "interface Message {",
            "readonly room: ChatRoom;",
        )
    }

    @Test
    fun `API2 listeners get the wrappers`() {
        // A listener, not a function StarLight calls: no `declare function onMessage` beside StarLight's own event.
        val message = event("starlight.api2.message")
        assertHas(message, "type MessageListener = (msg: Api2Message) => void;", "interface Api2Message {")
        assertTrue(!message.contains("declare function"))
        assertHas(event("starlight.api2.notification_posted"), "sessionManager: null")
    }

    @Test
    fun `classes, maps and enums`() {
        assertHas(api("EventEmitter"), "declare class EventEmitter {", "constructor(",
            "on(eventName: string, listener: (p1: JavaList<any>) => void): EventEmitter;",
            "off(eventName: string, listener: ((p1: JavaList<any>) => void) | null): EventEmitter;")
        assertHas(api("Env"), "declare const Env: JavaMap<string>;")
        assertTrue(!api("Utils").contains("constructor("))
        api("Log")
        api("Device")
        // A Java interface with one method takes a JS function.
        assertHas(api("Api"), "UIThread(func: () => any, ")
    }
}
