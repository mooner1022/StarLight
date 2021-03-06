package dev.mooner.starlight.plugincore.plugin

import dalvik.system.PathClassLoader
import java.io.File

class PluginClassLoader(
    private val loader: PluginLoader,
    parent: ClassLoader,
    private val config: PluginInfo,
    private val dataDir: File,
    private val file: File,
): PathClassLoader(file.path, parent) {

    private var pluginInit: StarlightPlugin? = null
    private var pluginState: java.lang.IllegalStateException? = null
    private val classes: MutableMap<String, Class<*>> = hashMapOf()
    val plugin: StarlightPlugin

    override fun findClass(name: String): Class<*> {
        return findClass(name, true)
    }

    fun findClass(name: String, checkGlobal: Boolean): Class<*> {
        if (name.startsWith("dev.mooner.starlight.plugincore")) throw ClassNotFoundException(name)
        var result = classes[name]

        if (result == null) {
            if (checkGlobal) {
                result = loader.getClass(name)
            }
            if (result == null) {
                result = super.findClass(name)
                if (result != null) {
                    loader.setClass(name, result)
                }
            }
            classes[name] = result!!
        }

        return result
    }

    @Synchronized
    fun initialize(plugin: StarlightPlugin) {
        require(plugin.javaClass.classLoader == this) {
            "Cannot initialize plugin outside of this class loader"
        }

        if (pluginInit != null) {
            throw IllegalArgumentException("Plugin already initialized!", pluginState)
        }
        pluginState = IllegalStateException("Initial initialization")
        pluginInit = plugin
        plugin.init(config, dataDir, file, this)
    }

    init {
        try {
            val jarClass: Class<*>
            try {
                jarClass = Class.forName(config.mainClass, false, this)
            } catch (e: ClassNotFoundException) {
                throw InvalidPluginException("Cannot find main class: ${config.mainClass}")
            }

            val pluginClass: Class<out StarlightPlugin>
            try {
                pluginClass = jarClass.asSubclass(StarlightPlugin::class.java)
            } catch (e: ClassCastException) {
                throw InvalidPluginException("Main class '${config.mainClass}' does not extend StarlightPlugin")
            }

            plugin = pluginClass.newInstance()
        } catch (e: IllegalAccessException) {
            throw InvalidPluginException("No public constructor")
        } catch (e: InstantiationException) {
            throw InvalidPluginException("Abnormal plugin type")
        }
    }
}