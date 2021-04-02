package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.language.Languages
import com.mooner.starlight.plugincore.plugin.Plugin
import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
        val name: String,
        val mainScript: String,
        val language: Languages,
        var isEnabled: Boolean,
        val listeners: MutableList<String>,
        val usedPlugins: MutableList<Plugin>,
        val packages: MutableList<String>
)

data class MutableProjectConfig(
        var name: String = "",
        var mainScript: String = "",
        var language: Languages = Languages.JS_RHINO,
        var isEnabled: Boolean = false,
        var listeners: MutableList<String> = mutableListOf(),
        var usedPlugins: MutableList<Plugin> = mutableListOf(),
        var packages: MutableList<String> = mutableListOf()
) {
    fun toProjectConfig(): ProjectConfig {
        return ProjectConfig(name,mainScript, language, isEnabled, listeners, usedPlugins, packages)
    }
}