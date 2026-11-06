/*
 * InMemoryConfig.kt created by Minki Moon(mooner1022) on 4/23/23, 11:26 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.configdsl.DataMap
import dev.mooner.starlight.plugincore.config.data.category.ConfigCategory
import dev.mooner.starlight.plugincore.config.data.category.internal.ConfigCategoryImpl

class InMemoryConfig(
    private val mData: DataMap
): ConfigData {

    override fun getData(): DataMap = mData

    override operator fun get(id: String): ConfigCategory =
        category(id)

    override fun contains(id: String): Boolean =
        categoryOrNull(id) != null

    override fun category(id: String): ConfigCategory =
        categoryOrNull(id) ?: ConfigCategoryImpl(mapOf())

    override fun categoryOrNull(id: String): ConfigCategory? {
        return mData[id]?.let(::ConfigCategoryImpl)
    }
}