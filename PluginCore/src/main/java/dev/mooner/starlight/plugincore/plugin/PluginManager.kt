package dev.mooner.starlight.plugincore.plugin

class PluginManager {

    private var _plugins: MutableSet<StarlightPlugin> =
        hashSetOf()
    val plugins: Set<StarlightPlugin>
        get() = _plugins

    fun getPluginById(id: String): StarlightPlugin? =
        plugins.find { it.info.id == id }

    fun getPluginByName(name: String): StarlightPlugin? =
        plugins.find { it.info.name == name }

    inline fun <reified T: StarlightPlugin> getPlugin(): T? =
        plugins.find { it is T } as T?

    internal fun addPlugin(plugin: StarlightPlugin) {
        _plugins += plugin
    }

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
        _plugins.clear()
    }
}