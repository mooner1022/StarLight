/*
 * ConfigCategoryImpl.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.starlight.plugincore.config.TypedString

class ConfigCategoryImpl(
    private val data: Map<String, TypedString>
): ConfigCategory {

    override operator fun contains(key: String): Boolean = data.containsKey(key)

    private operator fun get(key: String): Any? = data[key]?.cast()

    override fun getInt(key: String): Int? = data[key]?.castAs()

    override fun getLong(key: String): Long? = data[key]?.castAs()

    override fun getBoolean(key: String): Boolean? = data[key]?.castAs()

    override fun getString(key: String): String? = data[key]?.castAs()

    override fun getFloat(key: String): Float? = data[key]?.castAs()

    override fun getDouble(key: String): Double? = data[key]?.castAs()
}