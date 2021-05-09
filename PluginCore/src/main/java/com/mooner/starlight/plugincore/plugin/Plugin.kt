package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.project.Project

interface Plugin {
    val name: String

    fun isEnabled(): Boolean

    fun onEnable() {}

    fun onDisable() {}

    fun onError(e: Exception) {}

    fun onNetworkStateChanged(state: Int) {}

    fun onProjectStateChanged(state: Int, project: Project) {}
}