package com.mooner.starlight.plugincore.config

import com.mooner.starlight.plugincore.models.TypedString

class ConfigCategoryImpl(
    private val data: Map<String, TypedString>
): ConfigCategory {

    override operator fun contains(key: String): Boolean = data.containsKey(key)

    override fun getInt(key: String): Int? = data[key]?.castAs()

    override fun getLong(key: String): Long? = data[key]?.castAs()

    override fun getBoolean(key: String): Boolean? = data[key]?.castAs()

    override fun getString(key: String): String? = data[key]?.castAs()

    override fun getFloat(key: String): Float? = data[key]?.castAs()

    override fun getDouble(key: String): Double? = data[key]?.castAs()
}