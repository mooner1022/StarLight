/*
 * AllowedEventEntries.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.event

import dev.mooner.starlight.plugincore.project.event.ProjectEventManager.EXCLUDE_PREFIX

/**
 * A project's allowed event entries (event IDs, wildcards such as `starlight.*`, and exclusions that start with `!`)
 * edited as events to receive or not. Each change edits as few entries as it can and leaves the others as they were,
 * in their order. An entry that matches no registered event, such as a disabled plugin's or a typo, stays until it is
 * removed.
 *
 * [resolve] gives the registered event IDs an entry matches, without its `!`.
 */
class AllowedEventEntries(
    entries: Collection<String>,
    private val resolve: (String) -> Set<String> = ::resolveRegistered,
) {

    private val list: MutableList<String> = entries
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toMutableList()

    val entries: List<String>
        get() = list.toList()

    /** The registered events the entries allow, read as [ProjectEventManager.filterAllowedEvents] reads them. */
    val allowed: Set<String>
        get() = covered() - excluded()

    /** The entries that match no registered event. */
    val unknown: List<String>
        get() = list.filter { matches(it).isEmpty() }

    fun isAllowed(eventId: String): Boolean =
        eventId in allowed

    /** The events [entry] matches: those it allows, or those it takes out when it starts with `!`. */
    fun matches(entry: String): Set<String> =
        resolve(entry.trim().removePrefix(EXCLUDE_PREFIX))

    fun setAllowed(eventId: String, allow: Boolean) =
        setAllowed(listOf(eventId), allow)

    fun setAllowed(eventIds: Collection<String>, allow: Boolean) {
        for (id in eventIds)
            if (isAllowed(id) != allow)
                if (allow) allow(id) else disallow(id)
        tidy()
    }

    /** Whether an entry allows every event of [source], including those it adds later, as `source.*` does. */
    fun isAutomatic(source: String): Boolean =
        includes().any { it == wildcardOf(source) || it == WILDCARD_ALL }

    /** Whether [setAutomatic] can change [source]; not while `*` allows the events of every source. */
    fun canSetAutomatic(source: String): Boolean =
        WILDCARD_ALL !in list

    /**
     * Allows the events [source] adds later, or stops allowing them, and leaves those it has now as they are:
     * `source.*` with exclusions for the events not allowed, or the IDs of those that are.
     */
    fun setAutomatic(source: String, automatic: Boolean) {
        if (isAutomatic(source) == automatic || !canSetAutomatic(source))
            return
        val wildcard = wildcardOf(source)
        val events = resolve(wildcard)
        val wanted = allowed intersect events
        if (automatic) {
            list.removeAll { !it.startsWith(EXCLUDE_PREFIX) && it in events }
            list += wildcard
        } else {
            list.remove(wildcard)
            list.removeAll { it.startsWith(EXCLUDE_PREFIX) && matches(it).let { m -> m.isNotEmpty() && events.containsAll(m) } }
        }
        // Other entries may cover the same events, so each is put back as it was.
        for (id in events.sorted())
            when {
                id in wanted && !isAllowed(id) -> allow(id)
                id !in wanted && isAllowed(id) -> disallow(id)
            }
        tidy()
    }

    fun remove(entry: String) {
        list.remove(entry)
    }

    private fun allow(eventId: String) {
        list.remove(EXCLUDE_PREFIX + eventId)
        // A wildcard exclusion goes, and keeps out the rest of what it matched one by one.
        for (entry in list.filter { it.startsWith(EXCLUDE_PREFIX) && eventId in matches(it) }) {
            val index = list.indexOf(entry)
            list.removeAt(index)
            val rest = (matches(entry) - eventId).sorted().map { EXCLUDE_PREFIX + it }.filterNot(list::contains)
            list.addAll(index, rest)
        }
        if (eventId !in covered())
            list += eventId
    }

    private fun disallow(eventId: String) {
        list.remove(eventId)
        if (eventId in covered())
            list += EXCLUDE_PREFIX + eventId
    }

    /** Drops exclusions that no longer take anything out; those matching no registered event stay. */
    private fun tidy() {
        val covered = covered()
        list.removeAll { entry ->
            entry.startsWith(EXCLUDE_PREFIX) && matches(entry).let { it.isNotEmpty() && it.none(covered::contains) }
        }
    }

    private fun includes(): List<String> =
        list.filterNot { it.startsWith(EXCLUDE_PREFIX) }

    private fun covered(): Set<String> =
        includes().flatMapTo(hashSetOf(), ::matches)

    private fun excluded(): Set<String> =
        list.filter { it.startsWith(EXCLUDE_PREFIX) }.flatMapTo(hashSetOf(), ::matches)

    companion object {

        const val WILDCARD_ALL = "*"

        /** The entry that allows every event of [source], those it adds later included. */
        fun wildcardOf(source: String): String =
            "$source${ProjectEventManager.PATH_DELIMITER}$WILDCARD_ALL"

        private fun resolveRegistered(entry: String): Set<String> =
            ProjectEventManager.filterAllowedEvents(setOf(entry))
    }
}
