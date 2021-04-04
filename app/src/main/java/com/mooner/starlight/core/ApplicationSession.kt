package com.mooner.starlight.core

import android.annotation.SuppressLint
import android.content.Context
import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.project.ProjectLoader

@SuppressLint("StaticFieldLeak")
object ApplicationSession {
    lateinit var context: Context
    val projectLoader: ProjectLoader = ProjectLoader()
    val pluginLoader: PluginLoader = PluginLoader()
    val taskHandler: TaskHandler = TaskHandler()

    lateinit var plugins: List<Plugin>
}