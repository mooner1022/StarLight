package dev.mooner.starlight.plugincore.plugin

import android.view.View
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.data.Config

interface Plugin {

    val configObjects: List<CategoryConfigObject>

    @Deprecated(
        message = "Retained for legacy compatability, don't use it.",
        replaceWith = ReplaceWith("onConfigUpdated(config: Config)", "dev.mooner.starlight.plugincore.plugin.onConfigUpdated")
    )
    fun onConfigUpdated(updated: Map<String, Any>) {}

    fun onConfigUpdated(config: Config, updated: Set<String>) {}

    fun onConfigChanged(id: String, view: View?, data: Any) {}

    fun isEnabled(): Boolean
}