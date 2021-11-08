package com.mooner.starlight.plugincore.config

import com.mooner.starlight.plugincore.models.TypedString

class Config(
    private val data: Map<String, Map<String, TypedString>>
) {

    fun getCategory(id: String): ConfigCategory? {
        val categoryData = data[id]
        return if (categoryData == null)
            null
        else
            ConfigCategoryImpl(categoryData)
    }
}