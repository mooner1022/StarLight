package dev.mooner.starlight.plugincore.plugin

import android.content.Context
import dev.mooner.starlight.plugincore.project.Project

interface EventListener {

    fun onEnable() {}

    fun onDisable() {}

    fun onProjectCreated(project: Project) {}

    fun onProjectRemoved(project: Project) {}

    fun onProjectUpdated(project: Project) {}

    fun onNetworkStateChanged(state: Int) {}

    fun onSchemeAction(context: Context, isGlobal: Boolean, params: Map<String, String>) {}
}