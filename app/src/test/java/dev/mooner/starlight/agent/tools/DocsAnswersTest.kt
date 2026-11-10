package dev.mooner.starlight.agent.tools

import dev.mooner.starlight.agent.docs.TestDocs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The answers of `list_events`, `describe_event`, `list_apis`, `describe_api` and `read_guide` (docs/agent-docs.md §7). */
class DocsAnswersTest {

    private val answers = DocsAnswers(TestDocs.renderer, TestDocs.guides)

    private val catalog = TestDocs.catalog

    private fun assertFails(hint: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("expected a ToolException, got $error", error is ToolException)
        assertTrue(error!!.message!!, error.message!!.contains(hint))
    }

    @Test
    fun `list_events has a line per event, marked for a project`() {
        val lines = answers.listEvents()
        assertEquals(catalog.events.size + 1, lines.size)
        assertEquals(DocsAnswers.FUNCTIONS_NOTE, lines.last())
        for (event in catalog.events)
            assertTrue(event.id, lines.any { it.startsWith(event.id + " ") || it.startsWith(event.id + ":") || it.startsWith(event.id) })

        val marked = answers.listEvents { id ->
            when (id) {
                "starlight.message.create" -> DocsAnswers.Reception.RECEIVES
                "starlight.api2.message" -> DocsAnswers.Reception.NOT_IN_LEVELS
                else -> DocsAnswers.Reception.NOT_ALLOWED
            }
        }
        assertTrue(marked.single { "starlight.message.create" in it }.startsWith("[receives] starlight.message.create"))
        assertTrue(marked.single { "starlight.api2.message " in it || it.endsWith("starlight.api2.message") || "starlight.api2.message:" in it }
            .startsWith("[not in its API levels] "))
        assertEquals(catalog.events.size - 2, marked.count { it.startsWith("[doesn't receive] ") })
        assertEquals(DocsAnswers.FUNCTIONS_NOTE, marked.last())
    }

    @Test
    fun `list_apis lists every API in name order, or a project's`() {
        val lines = answers.listApis()
        assertEquals(catalog.apis.size, lines.size)
        val names = lines.map { line -> catalog.apis.first { line.startsWith(it.name + " ") || line.startsWith(it.name + ":") }.name }
        assertEquals(names.sortedBy(String::lowercase), names)
        assertEquals(2, answers.listApis(setOf("Api", "Utils")).size)
    }

    @Test
    fun `describe_api finds APIs regardless of case, and types`() {
        assertTrue(answers.describeApi("botmanager").contains("declare const BotManager"))
        assertTrue(answers.describeApi("ChatRoom").contains("interface ChatRoom"))
        assertTrue(answers.describeApi("dev.mooner.starlight.plugincore.chat.ChatRoom").contains("interface ChatRoom"))
        assertFails("list_apis") { answers.describeApi("NoSuchApi") }
    }

    @Test
    fun `describe_api shows types without docs too`() {
        // What Projects, Plugins and Languages return is documented for scripts, their internals left out.
        for (name in listOf("Project", "StarlightPlugin", "Language", "ProjectInfo", "PluginInfo"))
            assertTrue(name, answers.describeApi(name).also { TestDocs.preview("type-$name", it) }
                .let { it.contains("interface $name {") && !it.contains("Not documented") })
        val project = answers.describeApi("Project")
        assertTrue(project.contains("callFunction(name: string, args: any[] | JavaList<any>, onException: "))
        assertTrue(project.contains("readonly compiled: boolean;"))
        for (internal in listOf("coroutineContext", "runEvent", "lifecycle", "threadPoolName", "getScope"))
            assertTrue(internal, !project.contains(internal))
        // Hidden members name no types to read on: only what is shown does (PluginInfo's Version).
        for (name in listOf("Project", "Language", "StarlightPlugin"))
            for (hidden in listOf("Pipeline", "ProjectEvent", "ConfigData", "EventListener", "RootConfigOption"))
                assertTrue("$name: $hidden", !answers.describeApi(name).substringAfter("// Without docs:", "").contains(hidden))
        // A StarLight type without docs, from reflection, with the names Kotlin's metadata keeps.
        val version = answers.describeApi("Version")
        assertTrue(version, version.contains("interface Version {") && version.contains("Not documented"))
        assertTrue(answers.describeApi("PluginInfo").contains("// Without docs: Version (describe_api"))
        // The platform's: a pointer to their own references, under any of their names.
        for (name in listOf("StatusBarNotification", "android.service.notification.StatusBarNotification", "kotlin.Throwable", "Throwable"))
            assertTrue(name, answers.describeApi(name).contains("'s class, not StarLight's"))
        // Types shown in short say how to read them in full.
        assertTrue(answers.describeApi("BotManager").contains("// Declared in short above: Api2Room, Bot."))
    }

    @Test
    fun `describe_event takes an ID from list_events`() {
        assertTrue(answers.describeEvent("starlight.message.create").contains("declare function onMessage("))
        assertFails("list_events") { answers.describeEvent("onMessage") }
    }

    @Test
    fun `read_guide lists guides, and reads one or a section`() {
        val index = answers.readGuide(null).lines()
        assertEquals(TestDocs.guides.guides.size, index.size)
        for (id in listOf("app", "engines/neonjs", "engines/rhino", "events", "play-build", "projects", "testing"))
            assertTrue(id, index.any { it.startsWith("$id: ") })
        assertEquals(answers.readGuide(null), answers.readGuide(" "))

        assertTrue(answers.readGuide("events").startsWith("# Events\n"))
        assertTrue(answers.readGuide("events#api2-listeners").startsWith("# Events › API2 listeners\n"))
        assertEquals(answers.readGuide("events#api2-listeners"), answers.readGuide("guide:starlight:events#api2-listeners"))
        assertEquals(answers.readGuide("engines/rhino"), answers.readGuide("engines/rhino.md"))
        assertFails("read_guide without an id") { answers.readGuide("events#no-such-section") }
        assertFails("read_guide without an id") { answers.readGuide("guide:discord:setup") }
    }

    @Test
    fun `no answer is too long to read in one go`() {
        val sizes = catalog.apis.map { "api ${it.name}" to answers.describeApi(it.name).length } +
            catalog.events.map { "event ${it.id}" to answers.describeEvent(it.id).length } +
            TestDocs.guides.guides.map { "guide ${it.id}" to answers.readGuide(it.id).length } +
            listOf("list_events" to answers.listEvents().joinToString("\n").length, "list_apis" to answers.listApis().joinToString("\n").length) +
            catalog.undocumentedTypes.map { "type ${it.name}" to answers.describeApi(it.name).length }
        java.io.File("build/agent-docs-sizes.txt").writeText(sizes.sortedByDescending { it.second }.joinToString("\n") { "${it.second}\t${it.first}" })
        val tooLong = sizes.filter { it.second > MAX_CHARS }
        assertTrue("Longer than $MAX_CHARS characters: $tooLong", tooLong.isEmpty())
    }

    private companion object {
        /** About 6,000 tokens: one answer shouldn't take over the agent's context. */
        const val MAX_CHARS = 24_000
    }
}
