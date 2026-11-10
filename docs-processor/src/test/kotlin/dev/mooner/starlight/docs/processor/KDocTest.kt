package dev.mooner.starlight.docs.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KDocTest {

    @Test
    fun `reads summary, details and tags`() {
        val doc = KDoc.parse(
            """
            * The rooms bots can send to, from the messages received so far. Seen once each.
            *
            * Rooms appear after their first message.
            *
            * @param packageName The messenger to list rooms of.
            *   Not used yet.
            * @return Rooms in the order they were first seen.
            * @throws IllegalStateException When the bot is gone.
            * @since 1.0.1
            * @see guide:events#api2-listeners
            * @see Bot.send
            """.trimIndent()
        )!!
        assertEquals("The rooms bots can send to, from the messages received so far.", doc.summary)
        assertEquals("Seen once each.\n\nRooms appear after their first message.", doc.details)
        assertEquals("The messenger to list rooms of.\n  Not used yet.", doc.params["packageName"])
        assertEquals("Rooms in the order they were first seen.", doc.returns)
        assertEquals("When the bot is gone.", doc.throws["IllegalStateException"])
        assertEquals("1.0.1", doc.since)
        assertEquals(listOf("guide:events#api2-listeners"), doc.see)
    }

    @Test
    fun `code blocks keep their indentation, with or without margins`() {
        val code = "```js\nconst n = Notification.create(1)\n    .setTitle(\"Bot\")\n    .build();\n```"
        val margined = KDoc.parse(
            """
             * Posts one.
             *
             * ```js
             * const n = Notification.create(1)
             *     .setTitle("Bot")
             *     .build();
             * ```
            """
        )!!
        assertEquals(code, margined.details)
        val bare = KDoc.parse(" Posts one.\n\n ```js\n const n = Notification.create(1)\n     .setTitle(\"Bot\")\n     .build();\n ```\n @param id The ID.")!!
        assertEquals(code, bare.details)
        assertEquals("The ID.", bare.params["id"])
    }

    @Test
    fun `listeners name the events whose listeners a function takes`() {
        assertEquals("starlight.api2", KDoc.parse(" Adds a listener.\n\n @listeners starlight.api2\n @param eventName The name.")!!.listeners)
        assertNull(KDoc.parse(" Adds a listener.")!!.listeners)
    }

    @Test
    fun `a line starting in bold isn't a margin`() {
        val doc = KDoc.parse(" Runs while the setting\n **전역 전원** is on.\n\n @param sbn The notification.")!!
        assertEquals("Runs while the setting **전역 전원** is on.", doc.summary)
        assertEquals("The notification.", doc.params["sbn"])
        // A one-line comment that starts in bold.
        assertEquals("**전역 전원** must be on.", KDoc.parse(" **전역 전원** must be on. ")!!.summary)
    }

    @Test
    fun `a sentence ends only outside code`() {
        val (summary, details) = KDoc.splitSummary("Calls `a.b()` for each. More.")
        assertEquals("Calls `a.b()` for each.", summary)
        assertEquals("More.", details)
    }

    @Test
    fun `a summary without a period is the whole first paragraph`() {
        val (summary, details) = KDoc.splitSummary("The bot\nof this project\n\nDetails")
        assertEquals("The bot of this project", summary)
        assertEquals("Details", details)
    }

    @Test
    fun `links read as code`() {
        assertEquals("Sends `msg` to `Bot.send`; see the docs[1](https://x) and text.",
            KDoc.links("Sends [msg] to [Bot.send]; see the docs[1](https://x) and [text][Target]."))
    }

    @Test
    fun `suppress and empty comments`() {
        assertTrue(KDoc.parse("Internal.\n@suppress")!!.suppress)
        assertNull(KDoc.parse("  "))
    }

    @Test
    fun `tags inside code blocks are text`() {
        val doc = KDoc.parse("Example.\n\n```\n@param not a tag\n```")!!
        assertTrue(doc.params.isEmpty())
        assertEquals("```\n@param not a tag\n```", doc.details)
    }
}
