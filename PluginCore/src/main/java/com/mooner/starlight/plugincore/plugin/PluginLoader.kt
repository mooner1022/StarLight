package com.mooner.starlight.plugincore.plugin

import android.os.Environment
import com.mooner.starlight.plugincore.Info
import com.mooner.starlight.plugincore.Priority
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.event.EventListener
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.utils.Utils.Companion.readString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.util.jar.JarEntry
import java.util.jar.JarFile

class PluginLoader {
    private val classes: HashMap<String, Class<*>> = HashMap()
    private val loaders: LinkedHashMap<String, PluginClassLoader> = LinkedHashMap()
    private var listeners: MutableList<EventListener> = mutableListOf()

    companion object {
        private const val T = "PluginLoader"
    }

    @Suppress("DEPRECATION")
    private val defDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/")
    //private val dexDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/.dex/")

    fun loadPlugins(dir: File = defDirectory, onPluginLoad: ((name: String) -> Unit)? = null): List<Plugin> {
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }

        val lowPriorityQueue: MutableList<Pair<PluginConfig, File>> = mutableListOf()
        val list: MutableList<Plugin> = mutableListOf()

        for (file in dir.listFiles { it -> it.name.substringAfterLast(".") in listOf("apk", "jar") }?: arrayOf()) {
            try {
                val config: PluginConfig
                try {
                    config = getConfigFile(file)
                } catch (e: FileNotFoundException) {
                    Logger.e(T, e.toString())
                    throw InvalidPluginException(e.toString())
                } catch (e: IllegalStateException) {
                    Logger.e(T, e.toString())
                    throw InvalidPluginException(e.toString())
                }
                when(config.loadPriority) {
                    Priority.HIGH -> {
                        if (onPluginLoad != null) onPluginLoad(config.name)
                        val plugin = loadPlugin(config, file)
                        if (!plugin.pluginCoreVersion.isCompatibleWith(Info.PLUGINCORE_VERSION)) {
                            Logger.e(javaClass.simpleName, "Incompatible plugin version(${plugin.pluginCoreVersion}) found on [${plugin.name}]")
                        }
                        plugin.onEnable()
                        list.add(plugin)
                    }
                    Priority.MIDDLE -> {
                        CoroutineScope(Dispatchers.Default).launch {
                            if (onPluginLoad != null) onPluginLoad(config.name)
                            val plugin = loadPlugin(config, file)
                            if (!plugin.pluginCoreVersion.isCompatibleWith(Info.PLUGINCORE_VERSION)) {
                                Logger.e(javaClass.simpleName, "Incompatible plugin version(${plugin.pluginCoreVersion}) found on [${plugin.name}]")
                            }
                            plugin.onEnable()
                            list.add(plugin)
                        }
                    }
                    Priority.LOW -> {
                        lowPriorityQueue.add(Pair(config, file))
                    }
                }
                //PluginManager.plugins[config.name] = plugin
            } catch (e: Exception) {
                Logger.e(T, e.toString())
                if (Session.isDebugging) e.printStackTrace()
            }
        }
        return list
    }

    private fun loadPlugin(config: PluginConfig, file: File): StarlightPlugin {
        val parent = file.parentFile
        val dataDir = File(parent, config.name)

        if (dataDir.exists() && !dataDir.isDirectory) {
            throw InvalidPluginException(
                "Data folder for plugin ${config.name} exists and is not a directory"
            )
        } else if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        /*
        try {
            Session.logger.i(T, "Loading plugin ${config.fullName}")
            val dexClassLoader = PluginClassLoader(this, ClassLoader.getSystemClassLoader(), config, ))
            val loaded = dexClassLoader.loadClass(config.main)
            loaded.newInstance()
            Session.logger.i(T, "Loaded plugin ${config.fullName} (${file.name})")
        } catch (e: Exception) {
            Session.logger.e(T, "Failed to load plugin ${config.fullName} (${file.name}): $e")
            e.printStackTrace()
        }
        */

        Logger.i(T, "Loading plugin ${config.fullName}")
        val loader: PluginClassLoader
        try {
            loader = PluginClassLoader(this, javaClass.classLoader!!, config, dataDir, file)
            Logger.i(T, "Loaded plugin ${config.fullName} (${file.name})")
        } catch (e: InvalidPluginException) {
            Logger.e(T, "Failed to load plugin ${config.fullName} (${file.name}): $e")
            throw e
        }
        loaders[config.name] = loader
        return loader.plugin
    }

    private suspend fun loadPluginAsync(config: PluginConfig, file: File): StarlightPlugin {
        val parent = file.parentFile
        val dataDir = File(parent, config.name)

        if (dataDir.exists() && !dataDir.isDirectory) {
            throw InvalidPluginException(
                    "Data folder for plugin ${config.name} exists and is not a directory"
            )
        } else if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        /*
        try {
            Session.logger.i(T, "Loading plugin ${config.fullName}")
            val dexClassLoader = PluginClassLoader(this, ClassLoader.getSystemClassLoader(), config, ))
            val loaded = dexClassLoader.loadClass(config.main)
            loaded.newInstance()
            Session.logger.i(T, "Loaded plugin ${config.fullName} (${file.name})")
        } catch (e: Exception) {
            Session.logger.e(T, "Failed to load plugin ${config.fullName} (${file.name}): $e")
            e.printStackTrace()
        }
        */

        Logger.i(T, "Loading plugin ${config.fullName}")
        val loader: PluginClassLoader
        try {
            loader = PluginClassLoader(this, javaClass.classLoader!!, config, dataDir, file)
            Logger.i(T, "Loaded plugin ${config.fullName} (${file.name})")
        } catch (e: InvalidPluginException) {
            Logger.e(T, "Failed to load plugin ${config.fullName} (${file.name}): $e")
            throw e
        }
        loaders[config.name] = loader
        return loader.plugin
    }

    private fun getConfigFile(file: File): PluginConfig {
        var jar: JarFile? = null
        var stream: InputStream? = null

        try {
            jar = JarFile(file)
            val ent: JarEntry = jar.getJarEntry("res/raw/starlight.json") ?: throw FileNotFoundException("Cannot find starlight.json")

            stream = jar.getInputStream(ent)
            return PluginConfig.decode(stream.readString())
        } catch (e: IOException) {
            throw IllegalStateException("Cannot open starlight.json")
        } finally {
            jar?.close()
            stream?.close()
        }
    }

    fun getClass(name: String): Class<*>? {
        var cachedClass = classes[name]
        if (cachedClass != null) {
            return cachedClass
        } else {
            for (current in loaders.keys) {
                val loader = loaders[current]
                try {
                    cachedClass = loader!!.findClass(name, false)
                } catch (cnfe: ClassNotFoundException) {

                }
                if (cachedClass != null) {
                    return cachedClass
                }
            }
        }
        return null
    }

    fun registerListener(listener: EventListener) {
        val methods: HashSet<Method>
        try {
            val publicMethods = listener.javaClass.methods
            methods = HashSet(publicMethods.size, Float.MAX_VALUE)
            for (method in publicMethods)  {
                methods.add(method)
            }
            for (method in listener.javaClass.declaredMethods) {
                methods.add(method)
            }
        } catch (e: NoClassDefFoundError) {
            e.printStackTrace()
            Logger.e("PluginLoader", "Error while adding listener ${listener.javaClass.simpleName}: NoClassDefFound")
            return
        }

        for (method in methods) {
            //val annotation = method.getAnnotation(EventHandler::class.java) ?: continue
            val checkClass: Class<*> = method.parameterTypes[0]
            if (method.parameterTypes.size != 1 || !EventListener::class.java.isAssignableFrom(checkClass)) {
                Logger.e("PluginLoader", "Attempted to register invalid listener: ${listener.javaClass.simpleName}")
                continue
            }
            val eventClass = checkClass.asSubclass(EventListener::class.java)


            /*
             * UNFINISHED FUNCTION
             */
        }
    }

    fun setClass(name: String, clazz: Class<*>) {
        if (!classes.containsKey(name)) {
            classes[name] = clazz
        }
    }

    private fun removeClass(name: String) {
        if (classes.containsKey(name)) {
            classes.remove(name)
        }
    }
}