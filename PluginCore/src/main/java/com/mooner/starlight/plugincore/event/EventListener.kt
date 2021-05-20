package com.mooner.starlight.plugincore.event

import com.mooner.starlight.plugincore.project.Project

interface EventListener {

    fun onEnable() {}

    fun onDisable() {}

    fun onError(e: Exception) {}

    fun onProjectCreated(project: Project) {}

    fun onProjectRemoved(project: Project) {}

    fun onProjectUpdated(project: Project) {}

    fun onNetworkStateChanged(state: Int) {}
}