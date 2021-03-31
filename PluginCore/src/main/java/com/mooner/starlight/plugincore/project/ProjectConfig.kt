package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.plugin.Plugin
import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
        val name: String,
        val mainScript: String,
        val language: Languages,
        var isEnabled: Boolean,
        val listener: String,
        val usedPlugins: MutableList<Plugin>,
        val packages: MutableList<String>
)