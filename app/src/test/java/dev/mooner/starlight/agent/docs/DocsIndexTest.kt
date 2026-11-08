package dev.mooner.starlight.agent.docs

import dev.mooner.starlight.agent.docs.DocsIndex.Category
import dev.mooner.starlight.agent.docs.GuideLibrary.Guide
import dev.mooner.starlight.agent.tools.DocsAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The docs viewer's index: every document the tools give, by category and source, with what the tools return. */
class DocsIndexTest {

    private val guides = TestDocs.guides + listOf(Guide("setup", "Setup", "Connecting the bot.", "## Token\n\nFrom the portal.\n", module = "discord"))

    private val index = DocsIndex(DtsRenderer(TestDocs.catalog, guides), guides) { DtsRenderer.STARLIGHT }

    private val answers = DocsAnswers(DtsRenderer(TestDocs.catalog, guides), guides)

    private val catalog = TestDocs.catalog

    @Test
    fun `lists every guide, API and event the tools list`() {
        assertEquals(guides.guides.map { it.ref }.sorted(), index.guideEntries.map { it.key }.sorted())
        assertEquals(catalog.apis.map { it.name }.sorted(), index.apiEntries.map { it.key }.sorted())
        assertEquals(catalog.events.map { it.id }.sorted(), index.eventEntries.map { it.key }.sorted())

        val botManager = index.apiEntries.single { it.key == "BotManager" }
        assertEquals(catalog.docOf(catalog.api("BotManager")!!.cls)!!.summary, botManager.summary)
        assertEquals("object", botManager.detail)
        val message = index.eventEntries.single { it.key == "starlight.message.create" }
        assertEquals(catalog.event("starlight.message.create")!!.usage, message.detail)
    }

    @Test
    fun `types are the ones APIs and events lead to, without their own classes`() {
        val own = (catalog.apis.map { it.cls } + catalog.events.map { it.eventClass }).map { it.name }.toSet()
        val types = index.typeEntries
        assertTrue(types.isNotEmpty())
        assertTrue(types.none { it.key in own })
        assertEquals(catalog.closure.map { it.name }.filter { it !in own }.sorted(), types.filter { it.documented }.map { it.key }.sorted())
        // Undocumented ones are StarLight's or a plugin's own, which the agent sees from reflection.
        for (type in types.filterNot { it.documented })
            assertTrue(type.key, catalog.isOwn(Class.forName(type.key)))
    }

    @Test
    fun `groups by source, StarLight's first`() {
        val grouped = index.grouped(Category.GUIDE)
        assertEquals(listOf(DtsRenderer.STARLIGHT, "discord"), grouped.map { it.first })
        assertEquals(listOf("discord:setup"), grouped.last().second.map { it.key })
        assertEquals(grouped.first().second.map { it.title.lowercase() }.sorted(), grouped.first().second.map { it.title.lowercase() })
        // Without plugins, one group.
        assertEquals(listOf(DtsRenderer.STARLIGHT), index.grouped(Category.API).map { it.first })
    }

    @Test
    fun `contents are what the tools return`() {
        assertEquals(answers.readGuide("events"), index.content(Category.GUIDE, "events").text)
        assertTrue(index.content(Category.GUIDE, "events").isMarkdown)
        assertEquals(answers.readGuide("discord:setup#token"), index.content(Category.GUIDE, "guide:discord:setup#token").text)
        assertEquals(answers.describeApi("BotManager"), index.content(Category.API, "BotManager").text)
        assertFalse(index.content(Category.API, "BotManager").isMarkdown)
        assertEquals(answers.describeEvent("starlight.message.create"), index.content(Category.EVENT, "starlight.message.create").text)
        val type = index.typeEntries.first { it.documented }
        assertEquals(TestDocs.renderer.describeType(Class.forName(type.key)), index.content(Category.TYPE, type.key).text)
    }

    @Test
    fun `a missing document reads as the tool's error`() {
        assertTrue(index.content(Category.API, "NoSuchApi").text.startsWith("No API or type named NoSuchApi"))
        assertTrue(index.content(Category.GUIDE, "no-such-guide").text.startsWith("No guide no-such-guide"))
        assertEquals("No type a.B.", index.content(Category.TYPE, "a.B").text)
        assertNull(index.entry(Category.API, "NoSuchApi"))
        // A section's ref finds its guide.
        assertEquals("events", index.entry(Category.GUIDE, "guide:events#api2-listeners")?.key)
    }
}
