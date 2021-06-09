package com.mooner.starlight.plugincore.plugin

import android.os.Environment
import com.mooner.starlight.plugincore.core.Session.Companion.projectLoader
import dalvik.system.PathClassLoader
import java.io.File

class PluginClassLoader(
    private val loader: PluginLoader,
    parent: ClassLoader,
    private val config: PluginConfig,
    private val dataDir: File,
    private val file: File,
    @Suppress("deprecation")
    dexDirectory: File = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/.dex")
): PathClassLoader(file.path, parent){
    private var pluginInit: StarlightPlugin? = null
    private var pluginState: java.lang.IllegalStateException? = null
    val plugin: StarlightPlugin
    private val classes: HashMap<String, Class<*>> = HashMap()

    init {
        if (!dexDirectory.exists() || !dexDirectory.isDirectory) {
            dexDirectory.mkdirs()
        }

        try {
            val jarClass: Class<*>
            try {
                jarClass = Class.forName(config.main, true, this)
            } catch (e: ClassNotFoundException) {
                throw InvalidPluginException("Cannot find main class [${config.main}]")
            }

            val pluginClass: Class<out StarlightPlugin>
            try {
                pluginClass = jarClass.asSubclass(StarlightPlugin::class.java)
            } catch (e: ClassCastException) {
                throw InvalidPluginException("Main class [${config.main}] does not extend StarlightPlugin")
            }

            plugin = pluginClass.newInstance()
        } catch (e: IllegalAccessException) {
            throw InvalidPluginException("No public constructor")
        } catch (e: InstantiationException) {
            throw InvalidPluginException("Abnormal plugin type")
        }
    }

    override fun findClass(name: String): Class<*> {
        return findClass(name, true)
    }

    fun findClass(name: String, checkGlobal: Boolean): Class<*> {
        if (name.startsWith("com.mooner.starlight.")) throw ClassNotFoundException(name)
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

        if (this.pluginInit != null) {
            throw IllegalArgumentException("Plugin already initialized!", pluginState)
        }
        pluginState = IllegalStateException("Initial initialization")
        this.pluginInit = plugin
        plugin.init(loader, projectLoader, config, dataDir, file, this)
    }
}