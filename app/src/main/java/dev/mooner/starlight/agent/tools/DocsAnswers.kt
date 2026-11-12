package dev.mooner.starlight.agent.tools

import dev.mooner.starlight.agent.docs.DtsRenderer
import dev.mooner.starlight.agent.docs.GuideLibrary
import java.lang.reflect.MalformedParameterizedTypeException

/**
 * What the docs tools answer (docs/agent-docs.md §7), apart from looking up the project a question is about.
 */
class DocsAnswers(private val renderer: DtsRenderer, private val guides: GuideLibrary) {

    /** Whether a project receives an event, as `list_events` marks it. */
    enum class Reception(val mark: String) {
        RECEIVES("[receives]"),
        NOT_ALLOWED("[doesn't receive]"),
        /** Allowed, but owned by API levels the project didn't choose, so it never arrives. */
        NOT_IN_LEVELS("[not in its API levels]"),
    }

    /**
     * One line per event, marked by [reception] when a project is asked about, then [FUNCTIONS_NOTE] on the function
     * StarLight calls that isn't an event.
     */
    fun listEvents(reception: ((eventId: String) -> Reception)? = null): List<String> =
        renderer.events().map { event ->
            val line = renderer.eventLine(event)
            if (reception == null) line else "${reception(event.id).mark} $line"
        } + FUNCTIONS_NOTE

    fun describeEvent(id: String): String {
        val event = renderer.catalog.event(id) ?: throw ToolException("No event $id. list_events lists them.")
        return renderer.describeEvent(event)
    }

    /** One line per API, only those named in [names] when a project is asked about. */
    fun listApis(names: Set<String>? = null): List<String> =
        renderer.catalog.apis
            .filter { names == null || it.name in names }
            .sortedBy { it.name.lowercase() }
            .map(renderer::apiLine)

    /** An API, or a type the APIs and events lead to. */
    fun describeApi(name: String): String {
        val catalog = renderer.catalog
        return try {
            catalog.api(name)?.let(renderer::describeApi)
                ?: catalog.typeNamed(name)?.let(renderer::describeType)
                ?: throw ToolException("No API or type named $name. list_apis lists the APIs.")
        } catch (e: LinkageError) {
            // A plugin's class whose members name classes Android doesn't have.
            throw missingClass(name, e)
        } catch (e: TypeNotPresentException) {
            // The same, in a generic signature.
            throw missingClass(name, e)
        } catch (e: MalformedParameterizedTypeException) {
            throw missingClass(name, e)
        }
    }

    private fun missingClass(name: String, e: Throwable) =
        ToolException("$name can't be described: its members refer to a class that isn't there ($e).")

    /** The list of guides without an [id], plugins' as `<plugin ID>:<id>`; otherwise the guide or section it names. */
    fun readGuide(id: String?): String {
        if (id.isNullOrBlank())
            return guides.guides.joinToString("\n") { "${it.ref}: ${it.title} — ${it.summary}" }
        return guides.read(id) ?: throw ToolException("No guide $id. read_guide without an id lists them.")
    }

    companion object {
        const val FUNCTIONS_NOTE = "Not an event: onProjectButtonClicked(buttonId), called in a compiled project when " +
            "the user taps one of its custom buttons, whatever its API levels and allowed events " +
            "(guide:events#event-functions); and onUncaughtError(error, origin, detail), called with the errors of the " +
            "project's code that nothing caught (guide:events#uncaught-errors)."
    }
}
