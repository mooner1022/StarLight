package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.project.Project

interface Plugin {
    val pluginManager: PluginManager

    val usedProjects: List<Project>

    val name: String

    fun isEnabled(): Boolean

    fun onEnable() {}

    fun onError(e: Exception) {}

    fun onDisable() {}

    fun onFinish() {}
}