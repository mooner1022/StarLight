/*
 * ConfigCategoryImpl.kt created by Minki Moon(mooner1022) on 4/23/23, 8:23 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data.category.internal

import dev.mooner.starlight.plugincore.config.data.category.ConfigCategory
import kotlinx.serialization.json.*

internal class ConfigCategoryImpl(
    private val data: Map<String, JsonElement>
): ConfigCategory {

    override operator fun contains(key: String): Boolean =
        key in data

    private operator fun get(key: String): JsonPrimitive? =
        data[key]?.jsonPrimitive

    override fun getInt(key: String): Int? =
        get(key)?.int

    override fun getLong(key: String): Long? =
        get(key)?.long

    override fun getBoolean(key: String): Boolean? =
        get(key)?.boolean

    override fun getString(key: String): String? =
        get(key)?.content

    override fun getFloat(key: String): Float? =
        get(key)?.float

    override fun getDouble(key: String): Double? =
        get(key)?.double

    override fun getList(key: String): List<JsonElement>? =
        data[key]?.jsonArray

    override fun getObject(key: String): JsonObject? =
        data[key]?.jsonObject
}