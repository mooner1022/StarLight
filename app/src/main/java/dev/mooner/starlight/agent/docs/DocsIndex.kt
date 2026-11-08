package dev.mooner.starlight.agent.docs

import dev.mooner.starlight.agent.tools.DocsAnswers
import dev.mooner.starlight.agent.tools.ToolException
import java.lang.reflect.MalformedParameterizedTypeException

/**
 * Every document the agent's docs tools give, by category and by the module it comes from, for the docs viewer in
 * developer mode. The contents are exactly what the tools return, so the viewer shows what the agent reads.
 *
 * @param sourceOf `starlight`, or the ID of the plugin a type comes from ([AgentDocs.sourceOf] in the app).
 */
class DocsIndex(
    private val renderer: DtsRenderer,
    private val guides: GuideLibrary,
    private val sourceOf: (Class<*>) -> String,
) {

    enum class Category { GUIDE, API, EVENT, TYPE }

    class Entry(
        val category: Category,
        /** What [content] takes: a guide's ref, an API's name, an event's ID or a type's class name. */
        val key: String,
        val title: String,
        /** The one-line summary from the docs; `null` when there is none. */
        val summary: String?,
        /** A short line on what it is: a guide's ref, how an event is received, a type's full name. */
        val detail: String,
        /** `starlight`, or a plugin's ID. */
        val source: String,
        /** `false` for a type the agent sees from reflection only. */
        val documented: Boolean = true,
    )

    /** What [content] gives: Markdown for a guide, TypeScript declarations for the rest. */
    class Content(val text: String, val isMarkdown: Boolean)

    private val answers = DocsAnswers(renderer, guides)

    private val catalog: ScriptCatalog
        get() = renderer.catalog

    val guideEntries: List<Entry> by lazy {
        guides.guides.map { guide ->
            Entry(Category.GUIDE, guide.ref, guide.title.ifEmpty { guide.ref }, guide.summary.ifEmpty { null }, guide.ref, guide.module)
        }
    }

    val apiEntries: List<Entry> by lazy {
        catalog.apis.map { api ->
            val detail = buildString {
                append(if (api.isClass) "class" else "object")
                if (api.levels.isNotEmpty())
                    append(" · ").append(api.levels.joinToString())
            }
            Entry(Category.API, api.name, api.name, summaryOf(api.cls), detail, api.source)
        }
    }

    val eventEntries: List<Entry> by lazy {
        renderer.events().map { event ->
            Entry(Category.EVENT, event.id, event.id, summaryOf(event.eventClass), event.usage, event.source)
        }
    }

    /**
     * The documented types the APIs and events lead to, apart from the APIs' and events' own classes, then StarLight's
     * and the plugins' own types the agent sees without docs.
     */
    val typeEntries: List<Entry> by lazy {
        val own = (catalog.apis.map { it.cls } + catalog.events.map { it.eventClass }).toSet()
        val documented = catalog.closure.filter { it !in own }.map { cls ->
            Entry(Category.TYPE, cls.name, catalog.nameOf(cls), summaryOf(cls), cls.name, sourceOf(cls))
        }
        val undocumented = catalog.undocumentedTypes.filter { it !in own && catalog.isOwn(it) }.map { cls ->
            Entry(Category.TYPE, cls.name, catalog.nameOf(cls), null, cls.name, sourceOf(cls), documented = false)
        }
        documented + undocumented
    }

    fun entries(category: Category): List<Entry> = when (category) {
        Category.GUIDE -> guideEntries
        Category.API   -> apiEntries
        Category.EVENT -> eventEntries
        Category.TYPE  -> typeEntries
    }

    /** [category]'s entries by source: StarLight's first, then each plugin's, each sorted by title. */
    fun grouped(category: Category): List<Pair<String, List<Entry>>> =
        entries(category)
            .groupBy(Entry::source)
            .toList()
            .sortedWith(compareBy<Pair<String, List<Entry>>> { it.first != DtsRenderer.STARLIGHT }.thenBy { it.first })
            .map { (source, entries) -> source to entries.sortedWith(compareBy<Entry> { !it.documented }.thenBy { it.title.lowercase() }) }

    /** The entry [key] names in [category], or `null`. A guide's key may also be a section's ref. */
    fun entry(category: Category, key: String): Entry? =
        entries(category).firstOrNull { it.key == key }
            ?: if (category == Category.GUIDE) guides.guide(key)?.let { guide -> guideEntries.firstOrNull { it.key == guide.ref } } else null

    /**
     * What the tools return for [key]: `read_guide` for a guide or one of its sections, `describe_api` for an API or a
     * type, `describe_event` for an event. An error the tool would report, such as a plugin class that refers to a
     * class Android doesn't have, is the content.
     */
    fun content(category: Category, key: String): Content = try {
        when (category) {
            Category.GUIDE -> Content(answers.readGuide(key), isMarkdown = true)
            Category.API   -> Content(answers.describeApi(key), isMarkdown = false)
            Category.EVENT -> Content(answers.describeEvent(key), isMarkdown = false)
            Category.TYPE  -> Content(describeType(key), isMarkdown = false)
        }
    } catch (e: ToolException) {
        Content(e.message.orEmpty(), isMarkdown = false)
    }

    private fun describeType(className: String): String {
        val cls = (catalog.closure + catalog.undocumentedTypes).firstOrNull { it.name == className }
            ?: throw ToolException("No type $className.")
        return try {
            renderer.describeType(cls)
        } catch (e: LinkageError) {
            throw missingClass(className, e)
        } catch (e: TypeNotPresentException) {
            throw missingClass(className, e)
        } catch (e: MalformedParameterizedTypeException) {
            throw missingClass(className, e)
        }
    }

    private fun missingClass(name: String, e: Throwable) =
        ToolException("$name can't be described: its members refer to a class that isn't there ($e).")

    private fun summaryOf(cls: Class<*>): String? =
        catalog.docOf(cls)?.summary?.takeIf(String::isNotEmpty)
}
