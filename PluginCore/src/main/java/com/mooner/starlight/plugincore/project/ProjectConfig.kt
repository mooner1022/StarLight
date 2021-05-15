package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.plugin.Plugin
import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
        val name: String,
        val mainScript: String,
        val language: String,
        var isEnabled: Boolean,
        val createdMillis: Long = 0,
        val mainListener: String = "default",
        val listeners: MutableList<String>,
        val usedPlugins: MutableList<Plugin>,
        val packages: MutableList<String>
)

data class MutableProjectConfig(
        var name: String = "",
        var mainScript: String = "",
        var language: String = "",
        var isEnabled: Boolean = false,
        var createdMillis: Long = 0,
        val mainListener: String = "default",
        var listeners: MutableList<String> = mutableListOf(),
        var usedPlugins: MutableList<Plugin> = mutableListOf(),
        var packages: MutableList<String> = mutableListOf()
) {
    fun toProjectConfig(): ProjectConfig {
        return ProjectConfig(name,mainScript, language, isEnabled, createdMillis, mainListener, listeners, usedPlugins, packages)
    }
}