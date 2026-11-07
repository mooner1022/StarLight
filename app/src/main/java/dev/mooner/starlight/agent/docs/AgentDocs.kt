package dev.mooner.starlight.agent.docs

import android.content.Context
import dev.mooner.starlight.agent.docs.ScriptCatalog.ApiInfo
import dev.mooner.starlight.agent.docs.ScriptCatalog.EventInfo
import dev.mooner.starlight.agent.tools.DocsAnswers
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance

/**
 * The docs of the running app: its [ScriptCatalog], built from what is registered and rebuilt when the APIs or
 * events change, and its guides. See docs/agent-docs.md.
 */
class AgentDocs(context: Context) {

    private val appContext = context.applicationContext

    val guides: GuideLibrary by lazy { GuideLibrary.load(appContext.assets) }

    private val files: DocFiles by lazy { DocFiles.load(AgentDocs::class.java.classLoader!!) }

    @Volatile
    private var cached: Pair<List<Any>, ScriptCatalog>? = null

    fun catalog(): ScriptCatalog {
        val apis = Session.apiManager.getApis()
        val events = ProjectEventManager.find(ALL_EVENTS).sortedBy { it.qualifiedName }
        val levels = Session.apiLevelManager.getLevels()
        // Classes compare by identity, so a plugin loaded again (a new class loader) or changed levels make a new one.
        val key = listOf(
            apis.map { it.name to it.instanceClass },
            events.map { it.java },
            levels.map { Triple(it.id, it.apis.toSet(), it.events.toSet()) },
        )
        cached?.takeIf { it.first == key }?.let { return it.second }

        val catalog = ScriptCatalog(
            docs = files,
            apis = apis.map { api ->
                ApiInfo(
                    name = api.name,
                    cls = api.instanceClass,
                    isClass = api.instanceType == InstanceType.CLASS,
                    levels = levels.filter { api.name in it.apis }.map { it.id },
                    source = sourceOf(api.javaClass),
                )
            },
            events = events.mapNotNull { eventClass ->
                val id = ProjectEventManager.validateAndGetEventID(eventClass) ?: return@mapNotNull null
                val event = runCatching { eventClass.getInstance() }.getOrNull() ?: return@mapNotNull null
                EventInfo(
                    id = id,
                    eventClass = eventClass.java,
                    usage = event.usage,
                    functionName = event.functionName,
                    levels = levels.filter { id in it.events }.map { it.id },
                    arguments = event.scriptArguments.map { it.name to it.type.java },
                    source = sourceOf(eventClass.java),
                    listenerName = event.listenerName,
                )
            },
        )
        cached = key to catalog
        return catalog
    }

    fun renderer(): DtsRenderer =
        DtsRenderer(catalog(), guides)

    fun answers(): DocsAnswers =
        DocsAnswers(renderer(), guides)

    companion object {

        const val ALL_EVENTS = "*"

        /** `starlight`, or the ID of the plugin whose class loader loaded [cls]. */
        fun sourceOf(cls: Class<*>): String {
            val loader = cls.classLoader
            if (loader == null || loader === AgentDocs::class.java.classLoader)
                return DtsRenderer.STARLIGHT
            return Session.pluginManager.plugins.firstOrNull { it.javaClass.classLoader === loader }?.info?.id
                ?: DtsRenderer.STARLIGHT
        }
    }
}
