/*
 * MutableConfigCategory.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.starlight.plugincore.config.TypedString

class MutableConfigCategory(
    val data: MutableMap<String, TypedString>
): ConfigCategory {

    override operator fun contains(key: String): Boolean = data.containsKey(key)

    private operator fun get(key: String): Any? = data[key]?.cast()

    fun setAny(key: String, value: Any) {
        data[key] = TypedString.parse(value)
    }

    fun setInt(key: String, value: Int) = setAny(key, value)

    operator fun set(key: String, value: Int) = setInt(key, value)

    fun setLong(key: String, value: Long) = setAny(key, value)

    operator fun set(key: String, value: Long) = setLong(key, value)

    fun setBoolean(key: String, value: Boolean) = setAny(key, value)

    operator fun set(key: String, value: Boolean) = setBoolean(key, value)

    fun setString(key: String, value: String) = setAny(key, value)

    operator fun set(key: String, value: String) = setString(key, value)

    fun setFloat(key: String, value: Float) = setAny(key, value)

    operator fun set(key: String, value: Float) = setFloat(key, value)

    fun setDouble(key: String, value: Double) = setAny(key, value)

    operator fun set(key: String, value: Double) = setDouble(key, value)

    fun remove(key: String) {
        if (data.containsKey(key))
            data -= key
    }

    operator fun minusAssign(key: String) = remove(key)

    override fun getInt(key: String): Int? = data[key]?.castAs()

    override fun getLong(key: String): Long? = data[key]?.castAs()

    override fun getBoolean(key: String): Boolean? = data[key]?.castAs()

    override fun getString(key: String): String? = data[key]?.castAs()

    override fun getFloat(key: String): Float? = data[key]?.castAs()

    override fun getDouble(key: String): Double? = data[key]?.castAs()
}