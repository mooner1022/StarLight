package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.config.GlobalConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PluginManager {

    private var _plugins: MutableSet<StarlightPlugin> =
        hashSetOf()
    val plugins: Set<StarlightPlugin>
        get() = _plugins

    @Volatile
    private var _installed: List<InstalledPlugin> = emptyList()
    val installed: List<InstalledPlugin>
        get() = _installed

    private val _changes: MutableMap<String, PluginChange> = ConcurrentHashMap()
    val changes: Map<String, PluginChange>
        get() = _changes

    val requiresRestart: Boolean
        get() = _changes.isNotEmpty() || installed.any { it.isEnabledChanged() }

    fun getPluginById(id: String): StarlightPlugin? =
        plugins.find { it.info.id == id }

    fun getPluginByName(name: String): StarlightPlugin? =
        plugins.find { it.info.name == name }

    inline fun <reified T: StarlightPlugin> getPlugin(): T? =
        plugins.find { it is T } as T?

    fun getInstalled(key: String): InstalledPlugin? =
        installed.find { it.key == key }

    fun isPluginEnabled(id: String): Boolean =
        id !in getDisabledIds()

    fun setPluginEnabled(id: String, enabled: Boolean) {
        val disabled = getDisabledIds().let { if (enabled) it - id else it + id }
        GlobalConfig.edit {
            category(CATEGORY).setRaw(KEY_DISABLED, JsonArray(disabled.sorted().map(::JsonPrimitive)))
        }
    }

    fun getDisabledIds(): Set<String> =
        GlobalConfig.category(CATEGORY)
            .getList(KEY_DISABLED)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()

    fun install(installer: PluginInstaller, file: File, sha256: String? = null, repository: PluginRepository? = null): PluginChange {
        val id = installer.inspect(file).id
        val previous = getInstalled(id)?.info
        val info = installer.install(file, sha256, repository)
        val change = if (previous == null)
            PluginChange.Installed(info)
        else
            PluginChange.Updated(info, previous.version)
        _changes[id] = change
        for (entry in installed)
            if (entry.key != id && (entry.file == installer.fileOf(id) || !entry.file.exists()))
                _changes[entry.key] = PluginChange.Removed(entry.info)
        return change
    }

    fun uninstall(installer: PluginInstaller, key: String, keepData: Boolean) {
        val target = getInstalled(key)
        if (target?.isDuplicate == true) {
            installer.uninstall(target.file, null, keepData)
            _changes[key] = PluginChange.Removed(target.info)
            return
        }
        val info = target?.info ?: _changes[key]?.info
        installer.uninstall(target?.file ?: installer.fileOf(key), info, keepData)
        if (target != null)
            _changes[key] = PluginChange.Removed(info)
        else
            _changes -= key
    }

    @Deprecated("Use uninstall() with a PluginInstaller.")
    fun removePlugin(id: String): Boolean {
        try {
            val plugin = getPluginById(id)?: return false

            val file = plugin.originalFile
            if (!file.exists() || !file.isFile) return false

            file.delete()
            plugin.getInternalDataDirectory().deleteRecursively()
            plugin.getExternalDataDirectory().deleteRecursively()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun InstalledPlugin.isEnabledChanged(): Boolean {
        val id = info?.id ?: return false
        if (id in _changes)
            return false
        return when (state) {
            is PluginState.Disabled -> isPluginEnabled(id)
            is PluginState.Invalid  -> false
            else                    -> !isPluginEnabled(id)
        }
    }

    internal fun addPlugin(plugin: StarlightPlugin) {
        _plugins += plugin
    }

    internal fun setInstalled(installed: List<InstalledPlugin>) {
        _installed = installed
    }

    internal fun purge() {
        for (plugin in plugins) {
            plugin.onDisable()
        }
        _plugins.clear()
    }

    companion object {
        private const val CATEGORY     = "plugin"
        private const val KEY_DISABLED = "disabled_plugins"
    }
}
