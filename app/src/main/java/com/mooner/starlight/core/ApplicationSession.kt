package com.mooner.starlight.core

import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.plugincore.project.ProjectLoader

object ApplicationSession {
    val projectLoader: ProjectLoader = ProjectLoader()
    val pluginLoader: PluginLoader = PluginLoader()
    val taskHandler: TaskHandler = TaskHandler()

    lateinit var plugins: List<Plugin>
}