package dev.mooner.starlight.plugincore.plugin

import android.view.View
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.data.ConfigData
import java.io.File

interface Plugin {

    val info: PluginInfo

    fun getConfigStructure(): ConfigStructure

    @Deprecated(
        message = "Retained for legacy compatability, don't use it.",
        replaceWith = ReplaceWith("onConfigUpdated(config: Config)", "dev.mooner.starlight.plugincore.plugin.onConfigUpdated")
    )
    fun onConfigUpdated(updated: Map<String, Any>)

    fun onConfigUpdated(config: Config, updated: Map<String, Set<String>>)

    fun onConfigChanged(id: String, view: View?, data: Any)

    fun isEnabled(): Boolean

    fun getDataFolder(): File

    fun getAsset(path: String): File

    fun init(info: PluginInfo, dataDir: File, file: File, classLoader: ClassLoader)
}