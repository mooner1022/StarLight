/*
 * MutableConfigCategory.kt created by Minki Moon(mooner1022) on 4/23/23, 8:23 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data.category

import kotlinx.serialization.json.*

class MutableConfigCategory(
    val data: MutableMap<String, JsonElement>
): ConfigCategory {

    override operator fun contains(key: String): Boolean =
        data.containsKey(key)

    private operator fun get(key: String): JsonPrimitive? =
        data[key]?.jsonPrimitive

    fun setRaw(key: String, value: JsonElement) {
        data[key] = value
    }

    fun setInt(key: String, value: Int) =
        setRaw(key, JsonPrimitive(value))

    operator fun set(key: String, value: Int) =
        setInt(key, value)

    fun setLong(key: String, value: Long) =
        setRaw(key, JsonPrimitive(value))

    operator fun set(key: String, value: Long) =
        setLong(key, value)

    fun setBoolean(key: String, value: Boolean) =
        setRaw(key, JsonPrimitive(value))

    operator fun set(key: String, value: Boolean) =
        setBoolean(key, value)

    fun setString(key: String, value: String) =
        setRaw(key, JsonPrimitive(value))

    operator fun set(key: String, value: String) =
        setString(key, value)

    fun setFloat(key: String, value: Float) =
        setRaw(key, JsonPrimitive(value))

    operator fun set(key: String, value: Float) =
        setFloat(key, value)

    fun setDouble(key: String, value: Double) =
        setRaw(key, JsonPrimitive(value))

    operator fun set(key: String, value: Double) =
        setDouble(key, value)

    fun remove(key: String) {
        data -= key
    }

    operator fun minusAssign(key: String) = remove(key)

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