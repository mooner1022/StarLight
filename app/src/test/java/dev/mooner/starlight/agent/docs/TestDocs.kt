package dev.mooner.starlight.agent.docs

import dev.mooner.starlight.agent.docs.ScriptCatalog.ApiInfo
import dev.mooner.starlight.agent.docs.ScriptCatalog.EventInfo
import dev.mooner.starlight.api.CoreApis
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.library.LibraryManagerApi
import dev.mooner.starlight.plugincore.project.event.getInstance
import java.io.File

/**
 * The app's own docs as the tests read them: the doc files on the class path, the APIs and events of [CoreApis], and
 * the guides from their sources.
 */
object TestDocs {

    const val GUIDES = "src/main/assets/agent/guides"

    val files: DocFiles = DocFiles.load(TestDocs::class.java.classLoader!!)

    val catalog = ScriptCatalog(
        docs = files,
        // LibraryManager is PluginCore's, registered while a developer setting is on.
        apis = (CoreApis.earlyApis() + CoreApis.apis() + LibraryManagerApi()).map { api ->
            ApiInfo(api.name, api.instanceClass, api.instanceType == InstanceType.CLASS, emptyList(), DtsRenderer.STARLIGHT)
        },
        events = CoreApis.events.flatMap { (category, classes) ->
            classes.map { eventClass ->
                val event = eventClass.getInstance()
                EventInfo(
                    id = "starlight.$category.${event.id}",
                    eventClass = eventClass.java,
                    usage = event.usage,
                    functionName = event.functionName,
                    levels = emptyList(),
                    arguments = event.scriptArguments.map { it.name to it.type.java },
                    source = DtsRenderer.STARLIGHT,
                    listenerName = event.listenerName,
                )
            }
        },
    )

    val guides = GuideLibrary(
        File(GUIDES).walkTopDown().filter { it.isFile && it.extension == "md" }.mapNotNull { file ->
            GuideLibrary.parse(file.relativeTo(File(GUIDES)).path.removeSuffix(".md"), file.readText())
        }.toList()
    )

    val renderer = DtsRenderer(catalog, guides)

    /** Writes [text] to `build/agent-docs-preview/<name>.d.ts`, for reading what the agent gets. */
    fun preview(name: String, text: String) {
        File("build/agent-docs-preview").apply { mkdirs() }.resolve("$name.d.ts").writeText(text)
    }
}
