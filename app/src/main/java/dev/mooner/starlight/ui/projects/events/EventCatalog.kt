/*
 * EventCatalog.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.events

import android.content.Context
import android.widget.ImageView
import coil.load
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.ApiLevel
import dev.mooner.starlight.plugincore.project.event.AllowedEventEntries
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance

/** An event projects can receive, and the API levels that have it, in their order. */
internal class EventItem(
    val id: String,
    val event: ProjectEvent,
    val levels: List<ApiLevel>,
)

/** Where events come from, StarLight or a plugin: the first part of their IDs. */
internal class EventSource(
    val id: String,
    val name: String,
    val events: List<EventItem>,
)

internal object EventCatalog {

    const val STARLIGHT = "starlight"

    private const val SUMMARY_LIMIT = 3

    /** The registered events by source, StarLight's first; each source's as its levels list them. */
    fun load(): List<EventSource> {
        val levels = Session.apiLevelManager.getLevels()
        fun levelIndex(item: EventItem): Int =
            levels.indexOfFirst { item.id in it.events }.takeIf { it >= 0 } ?: levels.size
        fun indexInLevel(item: EventItem): Int =
            item.levels.firstOrNull()?.events?.indexOf(item.id) ?: 0
        return ProjectEventManager.filterAllowedEvents(setOf(AllowedEventEntries.WILDCARD_ALL))
            .mapNotNull { id ->
                val event = ProjectEventManager.findFirst(id)?.getInstance()
                    ?: return@mapNotNull null
                EventItem(id, event, levels.filter { id in it.events })
            }
            .groupBy { it.id.substringBefore(ProjectEventManager.PATH_DELIMITER) }
            .map { (source, items) ->
                EventSource(source, sourceName(source), items.sortedWith(compareBy({ levelIndex(it) }, { indexInLevel(it) }, { it.event.usage })))
            }
            .sortedWith(compareBy({ it.id != STARLIGHT }, EventSource::name))
    }

    /** Whether a project with the levels [levelIds] receives [item] when its entries allow it. */
    fun isAvailable(levelIds: Set<String>?, item: EventItem): Boolean =
        Session.apiLevelManager.hasEvent(levelIds, item.id)

    /**
     * What a project with [entries] and the levels [levelIds] receives, for its settings: how scripts receive the
     * first few events, one per line, and the sources whose later events it receives too.
     */
    fun summarize(context: Context, entries: Collection<String>, levelIds: Set<String>?): String {
        val allowed = AllowedEventEntries(entries)
        val sources = load()
        val received = sources
            .flatMap(EventSource::events)
            .filter { allowed.isAllowed(it.id) && isAvailable(levelIds, it) }
        if (received.isEmpty())
            return context.getString(R.string.event_picker_summary_none)
        return buildList {
            received.take(SUMMARY_LIMIT).mapTo(this) { it.event.usage }
            if (received.size > SUMMARY_LIMIT)
                add(context.getString(R.string.event_picker_more, received.size - SUMMARY_LIMIT))
            sources
                .filter { allowed.isAutomatic(it.id) }
                .mapTo(this) { context.getString(R.string.event_picker_auto_summary, it.name) }
        }.joinToString("\n")
    }

    private fun sourceName(source: String): String =
        if (source == STARLIGHT)
            "StarLight"
        else
            Session.pluginManager.getPluginById(source)?.info?.name ?: source
}

internal fun ImageView.loadEventSourceIcon(source: String) {
    if (source == EventCatalog.STARLIGHT) {
        load(R.drawable.ic_logo_transparent)
        return
    }
    val iconFile = Session.pluginManager.getPluginById(source)
        ?.getAsset("icon.png")
        ?.takeIf { it.isFile }
    load(iconFile ?: R.drawable.ic_round_plugins_24) {
        scale(Scale.FIT)
        transformations(RoundedCornersTransformation(resources.getDimension(R.dimen.lang_icon_corner_radius)))
    }
}
