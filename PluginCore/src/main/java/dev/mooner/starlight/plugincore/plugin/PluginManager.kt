package dev.mooner.starlight.plugincore.plugin

class PluginManager {

    internal var plugins: MutableSet<StarlightPlugin> = hashSetOf()

    fun getPluginById(id: String): StarlightPlugin? = plugins.find { it.info.id == id }

    fun getPluginByName(name: String): StarlightPlugin? = plugins.find { it.info.name == name }

    fun getPlugins(): List<StarlightPlugin> = plugins.toList()

    fun removePlugin(id: String): Boolean {
        try {
            val plugin = getPluginById(id)?: return false

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

    internal fun purge() {
        for (plugin in plugins) {
            plugin.onDisable()
        }
        plugins.clear()
    }
}