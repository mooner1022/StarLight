package dev.mooner.starlight.plugincore.plugin

import dev.mooner.configdsl.ConfigStructure
import dev.mooner.starlight.plugincore.config.data.ConfigData
import java.io.File

internal sealed interface Plugin {

    val info: PluginInfo

    fun getConfigStructure(): ConfigStructure

    fun onConfigUpdated(config: ConfigData, updated: Map<String, Set<String>>)

    fun onConfigValueUpdated(id: String, data: Any)

    fun isEnabled(): Boolean

    fun getInternalDataDirectory(): File

    fun getExternalDataDirectory(): File

    @Deprecated("Deprecated, use getExternalDataDirectory().")
    fun getDataFolder(): File

    fun getAsset(path: String): File

    fun init(info: PluginInfo, internalDir: File, file: File, classLoader: ClassLoader)
}