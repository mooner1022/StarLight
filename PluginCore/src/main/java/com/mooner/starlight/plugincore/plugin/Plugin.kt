package com.mooner.starlight.plugincore.plugin

import android.view.View
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.ConfigImpl

interface Plugin {

    val name: String

    val configObjects: List<CategoryConfigObject>

    @Deprecated(
        message = "Retained for legacy compatability, don't use it.",
        replaceWith = ReplaceWith("onConfigUpdated(config: Config)", "com.mooner.starlight.plugincore.plugin.onConfigUpdated")
    )
    fun onConfigUpdated(updated: Map<String, Any>) {}

    fun onConfigUpdated(config: ConfigImpl) {}

    fun onConfigChanged(id: String, view: View, data: Any) {}

    fun isEnabled(): Boolean
}