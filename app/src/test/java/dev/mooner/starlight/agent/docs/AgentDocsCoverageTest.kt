package dev.mooner.starlight.agent.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The checks of docs/agent-docs.md §11 on the app's own APIs and events: everything a script reaches is documented,
 * every event argument has a `@param`, and every guide reference resolves.
 */
class AgentDocsCoverageTest {

    private val docs = TestDocs.files
    private val catalog = TestDocs.catalog
    private val guides = TestDocs.guides

    @Test
    fun `the doc files are on the class path`() {
        assertEquals(DocFiles.CORE_MODULES.toSet(), docs.modules.map(DocFile::module).toSet())
    }

    @Test
    fun `everything scripts reach is documented`() {
        val missing = mutableListOf<String>()
        for (api in catalog.apis)
            if (catalog.docOf(api.cls) == null)
                missing += "API ${api.name}: ${api.cls.name}"
        for (cls in catalog.closure) {
            catalog.describe(cls).undocumented.forEach { missing += "${cls.name}: $it" }
            if (catalog.apis.any { it.cls == cls && it.isClass })
                catalog.describe(cls, isClass = true).undocumented.forEach { missing += "${cls.name} (static): $it" }
        }
        for (event in catalog.events) {
            val doc = catalog.docOf(event.eventClass)
            if (doc == null) {
                missing += "event ${event.id}: ${event.eventClass.name}"
                continue
            }
            for ((name, _) in event.arguments)
                if (name !in doc.params)
                    missing += "event ${event.id}: @param $name"
        }
        File("build/agent-docs-undocumented.txt").writeText(missing.joinToString("\n"))
        assertTrue("Undocumented (${missing.size}):\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun `guide references resolve`() {
        val refs = mutableListOf<Pair<String, String>>()
        for (file in docs.modules)
            for ((cls, doc) in file.classes) {
                doc.doc?.see?.forEach { refs += cls to it }
                for ((key, member) in doc.members)
                    member.doc?.see?.forEach { refs += "$cls#$key" to it }
            }
        for (guide in guides.guides)
            GUIDE_LINK.findAll(guide.body).forEach { refs += "guide ${guide.id}" to it.value }
        val prompt = File(SYSTEM_PROMPT).readText()
        READ_GUIDE.findAll(prompt).forEach { refs += "system prompt" to GuideLibrary.PREFIX + it.groupValues[1] }

        val broken = refs.filterNot { (_, ref) -> guides.resolves(ref) }
        assertTrue("Broken guide references:\n" + broken.joinToString("\n") { (from, ref) -> "$from -> $ref" }, broken.isEmpty())
    }

    @Test
    fun `every API, event and type renders`() {
        val renderer = TestDocs.renderer
        for (api in catalog.apis)
            assertTrue(api.name, renderer.describeApi(api).also { TestDocs.preview("api-${api.name}", it) }.contains(api.name))
        for (event in catalog.events)
            assertTrue(event.id, renderer.describeEvent(event).also { TestDocs.preview("event-${event.id}", it) }.contains(event.usage))
        for (cls in catalog.closure)
            assertTrue(cls.name, renderer.describeType(cls).also { TestDocs.preview("type-${catalog.nameOf(cls)}", it) }.isNotEmpty())
    }

    @Test
    fun `setting names the docs cite are the app's`() {
        // Guides and KDoc name settings as the app shows them, in bold: **전역 모듈 로드**, **설정 → 일반**.
        val texts = guides.guides.map { "guide ${it.id}" to it.body } +
            docs.modules.flatMap { file ->
                file.classes.flatMap { (cls, doc) ->
                    (listOfNotNull(doc.doc) + doc.members.values.mapNotNull { it.doc }).map { text ->
                        cls to listOfNotNull(text.summary, text.details, text.returns).plus(text.params.values).joinToString("\n")
                    }
                }
            }
        val sources = listOf("src/main/java", "src/main/res", "../PluginCore/src/main/java", "../PluginCore/src/main/res")
            .flatMap { File(it).walkTopDown().filter { file -> file.isFile && file.extension in setOf("kt", "xml") }.toList() }
            .joinToString("\n") { it.readText() }
            // KDoc cites the names too; only what the app shows counts.
            .replace(SETTING, "")
        val missing = texts.flatMap { (from, text) ->
            SETTING.findAll(text).flatMap { it.groupValues[1].split(" → ") }.filterNot { it in sources }.map { "$from: $it" } +
                // A setting cited by an English name the app doesn't show.
                CITED_SETTING.findAll(text).map { it.groupValues[1] }.filterNot(HANGUL::containsMatchIn).map { "$from: $it (not as the app shows it)" }
        }.distinct()
        assertTrue("Settings not found in the app:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    private companion object {
        const val SYSTEM_PROMPT = "src/main/java/dev/mooner/starlight/agent/runtime/SystemPrompt.kt"
        val GUIDE_LINK = Regex("guide:[A-Za-z0-9_/:#-]+")
        /** A bold name with Korean in it. */
        val SETTING = Regex("\\*\\*([^*\\n]*[가-힣][^*\\n]*)\\*\\*")
        /** A bold name right after "setting", which must be the app's own, Korean one. */
        val CITED_SETTING = Regex("settings?\\s+\\*\\*([^*]+)\\*\\*")
        val HANGUL = Regex("[가-힣]")
        val READ_GUIDE = Regex("read_guide ([a-z0-9_/-]+(?:#[a-z0-9-]+)?)")
    }
}
