/*
 * ConfigImpl.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.starlight.plugincore.config.TypedString

class ConfigImpl(
    private val data: Map<String, Map<String, TypedString>>
): Config {

    override operator fun get(id: String): ConfigCategory = getCategory(id)

    override fun getCategory(id: String): ConfigCategory = getCategoryOrNull(id)?: ConfigCategoryImpl(mapOf())

    override fun getCategoryOrNull(id: String): ConfigCategory? {
        val categoryData = data[id]
        return if (categoryData == null)
            null
        else
            ConfigCategoryImpl(categoryData)
    }
}