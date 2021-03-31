package com.mooner.starlight.plugincore.plugin

interface IPluginManager {
    fun getPlugin(pluginName: String): Plugin?

    fun hasPlugin(pluginName: String): Boolean

    fun isPluginEnabled(pluginName: String): Boolean

    val plugins: List<Plugin>
}