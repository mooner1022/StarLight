package dev.mooner.starlight.agent.docs

import dev.mooner.starlight.agent.docs.GuideLibrary.Guide
import dev.mooner.starlight.agent.docs.ScriptCatalog.ApiInfo
import dev.mooner.starlight.agent.tools.DocsAnswers
import dev.mooner.test.greeter.Greeter
import dev.mooner.test.greeter.Greeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A plugin's docs and guides, read from its package (docs/agent-docs.md §9). */
class PluginDocsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** As the docs processor writes it for plugin `greeter`, with `starlight.docs.packages` unset. */
    private val docFile = """
        {
          "format": 1,
          "module": "greeter",
          "packages": [],
          "classes": {
            "dev.mooner.test.greeter.Greeter": {
              "kind": "class",
              "doc": { "summary": "Greets people; a global while the plugin is loaded.", "details": "Read guide:greetings first, and guide:starlight:projects#api-levels.", "see": ["guide:greetings"] },
              "members": {
                "make(Ljava/lang/String;)Ldev/mooner/test/greeter/Greeting;": {
                  "kind": "function", "name": "make",
                  "params": [{ "name": "name", "type": "kotlin.String" }],
                  "returns": "dev.mooner.test.greeter.Greeting",
                  "doc": { "summary": "A greeting for `name`.", "params": { "name": "Who to greet, see guide:greetings#names." } }
                }
              }
            },
            "dev.mooner.test.greeter.Greeting": {
              "kind": "class",
              "doc": { "summary": "A greeting to send." },
              "members": {
                "getText()Ljava/lang/String;": { "kind": "getter", "name": "text", "returns": "kotlin.String", "doc": { "summary": "Its text." } }
              }
            }
          }
        }
    """.trimIndent()

    private val greetingsGuide = """
        ---
        title: Greetings
        summary: How to greet, and when.
        ---

        ## Names

        Use the name people chose. The [FAQ](guide:faq) and [projects](guide:starlight:projects) say more.
    """.trimIndent()

    private fun slp(vararg entries: Pair<String, String>): File =
        temp.newFile().also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                for ((name, text) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
        }

    @Test
    fun `reads the doc file and guides from the package, references without a module being the plugin's`() {
        val docs = PluginDocs.read(
            slp(
                "assets/starlight.json" to "{}",
                "starlight/agent/greeter.api.json" to docFile,
                "starlight/agent/other.api.json" to docFile.replace("greeter", "other"),
                "assets/agent/guides/greetings.md" to greetingsGuide,
                "assets/agent/guides/faq/start.md" to "---\ntitle: FAQ\nsummary: Questions.\n---\nAsk.",
                "assets/agent/guides/notes.md" to "# Without front matter",
                "assets/agent/guides/image.png" to "png",
            ),
            "greeter",
        )
        assertNotNull(docs.file)
        val file = docs.file!!
        assertEquals("greeter", file.module)
        val greeter = file.classes.getValue(Greeter::class.java.name).doc!!
        assertEquals(listOf("guide:greeter:greetings"), greeter.see)
        assertEquals("Read guide:greeter:greetings first, and guide:starlight:projects#api-levels.", greeter.details)
        val make = file.classes.getValue(Greeter::class.java.name).members.values.single().doc!!
        assertEquals("Who to greet, see guide:greeter:greetings#names.", make.params["name"])

        assertEquals(listOf("greeter:faq/start", "greeter:greetings"), docs.guides.map(Guide::ref).sorted())
        val guide = docs.guides.single { it.id == "greetings" }
        assertEquals("greeter", guide.module)
        assertTrue(guide.body, "[FAQ](guide:greeter:faq)" in guide.body && "[projects](guide:starlight:projects)" in guide.body)
    }

    @Test
    fun `a package without docs, or with a format this app doesn't read, has none`() {
        assertNull(PluginDocs.read(slp("assets/starlight.json" to "{}"), "greeter").file)
        assertNull(PluginDocs.read(slp("starlight/agent/greeter.api.json" to docFile.replace("\"format\": 1", "\"format\": 2")), "greeter").file)
        assertTrue(PluginDocs.read(slp("starlight/agent/greeter.api.json" to "not json"), "greeter").let { it.file == null && it.guides.isEmpty() })
    }

    @Test
    fun `qualify leaves references that name a module`() {
        assertEquals("guide:discord:setup#token", GuideLibrary.qualify("guide:setup#token", "discord"))
        assertEquals("guide:discord:engines/x", GuideLibrary.qualify("guide:engines/x", "discord"))
        assertEquals("guide:starlight:projects", GuideLibrary.qualify("guide:starlight:projects", "discord"))
        assertEquals("guide:other:setup", GuideLibrary.qualify("guide:other:setup", "discord"))
        assertEquals("see guide:discord:setup: it explains", GuideLibrary.qualify("see guide:setup: it explains", "discord"))
        assertEquals("guide:setup", GuideLibrary.qualify("guide:setup", GuideLibrary.MODULE))
    }

    @Test
    fun `plugins' guides are read by their plugin's ID, and never replace the app's`() {
        val guides = TestDocs.guides + listOf(
            Guide("setup", "Setup", "Connecting the bot.", "## Token\n\nFrom the portal.\n", module = "discord"),
            Guide("events", "Discord events", "Events of the plugin.", "Body\n", module = "discord"),
        )
        assertTrue(guides.read("discord:setup")!!.startsWith("# Setup\nConnecting the bot."))
        assertEquals(guides.read("discord:setup#token"), guides.read("guide:discord:setup#token"))
        assertTrue(guides.read("discord:setup#token")!!.startsWith("# Setup › Token"))
        assertTrue(guides.resolves("guide:discord:setup#token"))
        assertFalse(guides.resolves("guide:discord:setup#nothing"))
        assertEquals("Setup: Token", guides.summaryOf("guide:discord:setup#token"))
        assertNull(guides.read("setup"))
        assertNull(guides.read("guide:slack:setup"))

        // The app's events guide stays the app's.
        assertEquals(TestDocs.guides.read("events"), guides.read("events"))
        assertEquals(TestDocs.guides.read("events"), guides.read("guide:starlight:events"))
        assertTrue(guides.read("discord:events")!!.startsWith("# Discord events"))

        val index = DocsAnswers(TestDocs.renderer, guides).readGuide(null).lines()
        assertEquals(TestDocs.guides.guides.size + 2, index.size)
        assertEquals(listOf("discord:events: Discord events — Events of the plugin.", "discord:setup: Setup — Connecting the bot."), index.takeLast(2))
    }

    @Test
    fun `a plugin's API is described from its docs, with the types it leads to and its guides`() {
        val plugin = PluginDocs.read(slp("starlight/agent/greeter.api.json" to docFile, "assets/agent/guides/greetings.md" to greetingsGuide), "greeter")
        val catalog = ScriptCatalog(
            docs = TestDocs.files + listOfNotNull(plugin.file),
            apis = listOf(ApiInfo("Greeter", Greeter::class.java, isClass = false, levels = emptyList(), source = "greeter")),
            events = emptyList(),
        )
        val guides = TestDocs.guides + plugin.guides
        val answers = DocsAnswers(DtsRenderer(catalog, guides), guides)

        assertTrue(answers.listApis().single(), "Greets people; a global while the plugin is loaded." in answers.listApis().single())
        val described = answers.describeApi("Greeter")
        TestDocs.preview("plugin-greeter", described)
        assertTrue(described, "* A greeting for `name`." in described && "@param name Who to greet, see guide:greeter:greetings#names." in described)
        assertTrue(described, "make(name: string): Greeting;" in described)
        // Greeting is the plugin's and documented, so it is declared too.
        assertTrue(described, "interface Greeting" in described && "/** Its text. */" in described)
        assertTrue(described, "// Related: guide:greeter:greetings — How to greet, and when." in described)
        assertTrue(catalog.docs.isDocumented(Greeting::class.java))
        assertFalse(catalog.docs.isDocumented(PluginDocsTest::class.java))
    }
}
