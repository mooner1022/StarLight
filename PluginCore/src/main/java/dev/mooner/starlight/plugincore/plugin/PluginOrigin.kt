package dev.mooner.starlight.plugincore.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginRepository(
    val name: String,
    val url: String,
)

@Serializable
data class PluginOrigin(
    val repository: PluginRepository? = null,
    val sha256: String,
    val installedAt: Long,
) {
    val isLocalFile: Boolean
        get() = repository == null
}
