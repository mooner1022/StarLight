package com.mooner.starlight.plugincore.plugin

import android.view.View
import com.mooner.starlight.plugincore.language.ConfigObject

interface Plugin {
    val name: String

    val configObjects: List<ConfigObject>

    fun onConfigUpdated(updated: Map<String, Any>) {}

    fun onConfigChanged(id: String, view: View, data: Any) {}

    fun isEnabled(): Boolean
}