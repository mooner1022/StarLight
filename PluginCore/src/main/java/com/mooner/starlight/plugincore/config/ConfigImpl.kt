package com.mooner.starlight.plugincore.config

import com.mooner.starlight.plugincore.models.TypedString

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