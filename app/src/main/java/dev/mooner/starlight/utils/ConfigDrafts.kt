/*
 * ConfigDrafts.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

import dev.mooner.configdsl.DataMap
import dev.mooner.configdsl.MutableDataMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * What a settings screen with a save button holds until it is saved: the values the user changed, by category and
 * setting. A screen keeps them through its own end, as when the device is turned or the theme changes, by writing
 * them into its saved state and laying them over the stored values when it is made anew.
 */
internal object ConfigDrafts {

    /** The changes as text, for a screen's saved state. */
    fun encode(changed: DataMap): String =
        Json.encodeToString<Map<String, Map<String, JsonElement>>>(changed)

    /** The changes [encode] wrote; none for text that is missing or can't be read. */
    fun decode(text: String?): MutableDataMap {
        if (text.isNullOrEmpty())
            return hashMapOf()
        return runCatching { Json.decodeFromString<Map<String, Map<String, JsonElement>>>(text) }
            .getOrNull()
            ?.mapValuesTo(hashMapOf()) { (_, values) -> values.toMutableMap() }
            ?: hashMapOf()
    }

    /**
     * A copy a screen can edit freely. Handed the stored map itself, the screen would change it as the user types,
     * and leaving without saving would keep those values in memory, to be written with the next save of anything.
     */
    fun editableCopy(stored: DataMap): MutableDataMap =
        stored.mapValuesTo(hashMapOf()) { (_, values) -> values.toMutableMap() }

    /** Lays [changed] over [data], which then shows what the user had set. */
    fun overlay(data: MutableDataMap, changed: DataMap): MutableDataMap {
        for ((category, values) in changed)
            data.getOrPut(category) { mutableMapOf() }.putAll(values)
        return data
    }
}
