package com.mooner.starlight.plugincore.plugin

import android.view.View
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.ConfigObject

interface Plugin {
    val name: String

    val configObjects: List<CategoryConfigObject>

    fun onConfigUpdated(updated: Map<String, Any>) {}

    fun onConfigChanged(id: String, view: View, data: Any) {}

    fun isEnabled(): Boolean
}