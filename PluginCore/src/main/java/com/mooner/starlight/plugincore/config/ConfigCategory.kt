package com.mooner.starlight.plugincore.config

import com.mooner.starlight.plugincore.models.TypedString

class ConfigCategory(
    private val data: Map<String, TypedString>
) {

    operator fun contains(key: String): Boolean = data.containsKey(key)

    fun getInt(key: String): Int? = data[key]?.castAs()

    fun getInt(key: String, default: Int): Int = getInt(key)?: default

    fun getLong(key: String): Long? = data[key]?.castAs()

    fun getLong(key: String, default: Long): Long = getLong(key)?: default

    fun getBoolean(key: String): Boolean? = data[key]?.castAs()

    fun getBoolean(key: String, default: Boolean): Boolean = getBoolean(key)?: default

    fun getString(key: String): String? = data[key]?.castAs()

    fun getString(key: String, default: String): String = getString(key)?: default

    fun getFloat(key: String): Float? = data[key]?.castAs()

    fun getFloat(key: String, default: Float): Float = getFloat(key)?: default

    fun getDouble(key: String): Double? = data[key]?.castAs()

    fun getDouble(key: String, default: Double): Double = getDouble(key)?: default

}