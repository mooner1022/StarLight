/*
 * ConfigImpl.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

import dev.mooner.starlight.plugincore.config.category.ConfigCategory
import dev.mooner.starlight.plugincore.config.category.internal.ConfigCategoryImpl

class ConfigImpl(
    private val mData: Map<String, Map<String, TypedString>>
): Config {

    override fun getData(): ConfigData = mData

    override operator fun get(id: String): ConfigCategory =
        category(id)

    override fun contains(id: String): Boolean =
        categoryOrNull(id) != null

    override fun category(id: String): ConfigCategory =
        categoryOrNull(id)?: ConfigCategoryImpl(mapOf())

    override fun categoryOrNull(id: String): ConfigCategory? {
        val categoryData = mData[id]
        return if (categoryData == null)
            null
        else
            ConfigCategoryImpl(categoryData)
    }
}