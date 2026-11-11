package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import java.util.Collections
import java.util.IdentityHashMap

/**
 * What is registered with the app at one moment: the languages, widgets, APIs, API levels and project events.
 * Taken before a plugin is loaded, it tells what the plugin added, so that it can be taken out again when the plugin
 * fails as it is enabled. Left in, the half of a plugin that got registered stays in use: its language is offered
 * for new projects, its API is handed to every project that compiles, and it has no working plugin behind it.
 *
 * Plugins are loaded one after another, so what came between the snapshot and the failure is the failed plugin's.
 * What a plugin registers elsewhere, as its own listeners for the app's events, is not covered.
 */
internal class RegistrationSnapshot private constructor(
    private val languages: Set<Language>,
    private val widgets: Set<String>,
    private val apis: Set<Api<*>>,
    private val levels: Set<String>,
    private val events: Set<String>,
) {

    /** Takes out everything registered since. Returns what it was, in words, for the log. */
    fun removeNewer(): List<String> {
        val removed = mutableListOf<String>()
        for (language in Session.languageManager.getLanguages().filter { it !in languages }) {
            Session.languageManager.remove(language)
            removed += "language ${language.id}"
        }
        for (id in Session.widgetManager.getDefinitions().map { it.id }.filter { it !in widgets }) {
            Session.widgetManager.remove(id)
            removed += "widget $id"
        }
        for (api in Session.apiManager.getApis().filter { it !in apis }) {
            Session.apiManager.remove(api)
            removed += "API ${api.name}"
        }
        for (id in Session.apiLevelManager.getLevels().map { it.id }.filter { it !in levels }) {
            Session.apiLevelManager.remove(id)
            removed += "API level $id"
        }
        for (id in ProjectEventManager.registeredIds().filter { it !in events }) {
            ProjectEventManager.unregister(id)
            removed += "event $id"
        }
        return removed
    }

    companion object {

        fun take(): RegistrationSnapshot =
            RegistrationSnapshot(
                // By identity: two languages or APIs may count as equal and still be different registrations.
                languages = identitySetOf(Session.languageManager.getLanguages().asList()),
                widgets = Session.widgetManager.getDefinitions().mapTo(hashSetOf()) { it.id },
                apis = identitySetOf(Session.apiManager.getApis()),
                levels = Session.apiLevelManager.getLevels().mapTo(hashSetOf()) { it.id },
                events = ProjectEventManager.registeredIds(),
            )

        private fun <T> identitySetOf(items: Collection<T>): Set<T> =
            Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(items) }
    }
}
