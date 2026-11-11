package dev.mooner.starlight.plugincore.plugin

import android.content.Context
import dalvik.system.PathClassLoader
import java.io.File

class PluginClassLoader(
    context: Context,
    parent: ClassLoader,
    dexFile: File,
    nativeLibPath: String?,
    private val loader: PluginLoader,
    private val pluginInfo: PluginInfo,
    private val file: File,
): PathClassLoader(dexFile.path, nativeLibPath, parent), PluginHostClassLoader {

    private val internalDir: File

    private var pluginInit: StarlightPlugin? = null
    private var pluginState: IllegalStateException? = null
    private val classes: MutableMap<String, Class<*>> = hashMapOf()
    override val plugin: StarlightPlugin

    override fun findClass(name: String): Class<*> {
        return findClass(name, true)
    }

    override fun findClass(name: String, checkGlobal: Boolean): Class<*> {
        if (name.startsWith("dev.mooner.starlight.plugincore"))
            throw ClassNotFoundException(name)
        var result = classes[name]

        if (result == null) {
            result = runCatching { super.findClass(name) }.getOrNull()
            if (result == null && checkGlobal) {
                result = loader.getClass(name)
            }
            result?.also {
                classes[name] = it
                loader.setClass(name, it)
            }
        }

        return result ?: throw ClassNotFoundException(name)
    }

    @Synchronized
    override fun initialize(plugin: StarlightPlugin) {
        require(plugin.javaClass.classLoader == this) {
            "Cannot initialize plugin outside of this class loader"
        }

        if (pluginInit != null) {
            throw IllegalArgumentException("Plugin already initialized!", pluginState)
        }
        pluginState = IllegalStateException("Initial initialization")
        pluginInit = plugin
        plugin.init(pluginInfo, internalDir, file, this)
    }

    init {
        try {
            val mainClass: Class<*>
            try {
                mainClass = Class.forName(pluginInfo.mainClass, false, this)
            } catch (e: ClassNotFoundException) {
                // The cause says why: a class the main one needs may be what is missing, not the main class.
                throw InvalidPluginException("Unable to find main class: ${pluginInfo.mainClass}").apply { initCause(e) }
            }

            val pluginClass: Class<out StarlightPlugin>
            try {
                pluginClass = mainClass.asSubclass(StarlightPlugin::class.java)
            } catch (e: ClassCastException) {
                throw InvalidPluginException("Main class '${pluginInfo.mainClass}' does not extend StarlightPlugin").apply { initCause(e) }
            }
            internalDir = context.filesDir

            plugin = pluginClass.newInstance()
        } catch (e: IllegalAccessException) {
            throw InvalidPluginException("No public constructor").apply { initCause(e) }
        } catch (e: InstantiationException) {
            throw InvalidPluginException("Abnormal plugin type").apply { initCause(e) }
        }
    }
}