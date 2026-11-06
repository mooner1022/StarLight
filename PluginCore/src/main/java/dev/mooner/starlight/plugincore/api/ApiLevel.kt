/*
 * ApiLevel.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.api

/**
 * A set of script objects and project events that a project chooses as a whole, such as StarLight's own APIs or the
 * API of another bot app it is compatible with. Levels may share objects; a project that chooses several gets each
 * object once.
 */
data class ApiLevel(
    val id: String,
    val name: String,
    /** The heading the level is listed under when a project chooses its levels. */
    val group: String,
    val description: String? = null,
    /** Names of the [Api]s in this level. */
    val apis: Set<String>,
    /** IDs of the project events in this level. */
    val events: Set<String>,
    /** Chosen for a new project unless the user changes it. */
    val isDefault: Boolean = false,
    /** The language a new project with this level starts with, when one suits it better. */
    val preferredLanguageId: String? = null,
)

/**
 * The [ApiLevel]s projects choose from. An API or event that no level names belongs to none, so every project has it,
 * as plugins' do. A project whose levels are `null` predates levels and has everything.
 */
class ApiLevelManager {

    private val levels: MutableMap<String, ApiLevel> = LinkedHashMap()

    /** Adds [level]; false, and nothing changes, if one with its id is there already. */
    fun register(level: ApiLevel): Boolean =
        synchronized(levels) { levels.putIfAbsent(level.id, level) == null }

    fun getLevels(): List<ApiLevel> =
        synchronized(levels) { levels.values.toList() }

    fun getLevel(id: String): ApiLevel? =
        synchronized(levels) { levels[id] }

    /** The levels a new project starts with. */
    val defaultLevelIds: Set<String>
        get() = getLevels().filter(ApiLevel::isDefault).mapTo(linkedSetOf(), ApiLevel::id)

    /** Whether a project that chose [levelIds] gets the API named [apiName]. */
    fun hasApi(levelIds: Set<String>?, apiName: String): Boolean =
        isIncluded(levelIds) { apiName in it.apis }

    /** Whether a project that chose [levelIds] can be called by the event [eventId]. */
    fun hasEvent(levelIds: Set<String>?, eventId: String): Boolean =
        isIncluded(levelIds) { eventId in it.events }

    private inline fun isIncluded(levelIds: Set<String>?, contains: (ApiLevel) -> Boolean): Boolean {
        if (levelIds == null)
            return true
        val owners = getLevels().filter(contains)
        return owners.isEmpty() || owners.any { it.id in levelIds }
    }

    internal fun purge() =
        synchronized(levels) { levels.clear() }
}
