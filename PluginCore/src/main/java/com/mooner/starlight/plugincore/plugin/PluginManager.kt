package com.mooner.starlight.plugincore.plugin

class PluginManager {

    internal var plugins: MutableSet<Plugin> = hashSetOf()

    fun getPluginById(id: String): Plugin? = plugins.find { (it as StarlightPlugin).info.id == id }

    fun getPluginByName(name: String): Plugin? = plugins.find { (it as StarlightPlugin).info.name == name }

    fun getPlugins(): List<Plugin> = plugins.toList()

    fun removePlugin(id: String): Boolean {
        try {
            val plugin = getPluginById(id)?: return false
            if (plugin !is StarlightPlugin) return false

            val file = plugin.file
            if (!file.exists() || !file.isFile) return false

            file.delete()
            plugin.getDataFolder().deleteRecursively()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}