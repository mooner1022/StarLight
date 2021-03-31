package com.mooner.starlight.plugincore.plugin

abstract class PluginManager: IPluginManager {
    private val _plugins: HashMap<String, StarlightPlugin> = hashMapOf()

    override fun getPlugin(pluginName: String): StarlightPlugin? = _plugins[pluginName]

    override fun hasPlugin(pluginName: String): Boolean = _plugins.containsKey(pluginName)

    override fun isPluginEnabled(pluginName: String): Boolean {
        return if (_plugins.containsKey(pluginName)) true
        else false
    }

    fun isPluginEnabled(plugin: Plugin): Boolean {
        return isPluginEnabled(plugin.javaClass.name)
    }

    override val plugins: List<Plugin>
        get() = _plugins.values.toList()
}