package com.mooner.starlight.plugincore.plugin

import android.os.Environment
import com.mooner.starlight.plugincore.core.Info
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Version
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.utils.Utils.Companion.readString
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.util.jar.JarEntry
import java.util.jar.JarFile

class PluginLoader {
    private val classes: MutableMap<String, Class<*>> = hashMapOf()
    private val loaders: MutableMap<String, PluginClassLoader> = LinkedHashMap()

    companion object {
        private const val T = "PluginLoader"
    }

    @Suppress("DEPRECATION")
    private val defDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/")
    //private val dexDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/.dex/")

    fun loadPlugins(dir: File = defDirectory, onPluginLoad: ((name: String) -> Unit)? = null): Set<Plugin> {
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }

        val pluginInfos: MutableMap<String, Pair<File, PluginInfo>> = hashMapOf()
        for (file in dir.listFiles { it -> it.extension in listOf("apk", "jar") }?: arrayOf()) {
            val info: PluginInfo
            try {
                info = loadInfoFile(file)
                pluginInfos[info.id] = Pair(file, info)
            } catch (e: FileNotFoundException) {
                Logger.e(T, e.toString())
                //throw InvalidPluginException(e.toString())
            } catch (e: IllegalStateException) {
                Logger.e(T, e.toString())
                //throw InvalidPluginException(e.toString())
            } catch (e: Exception) {
                Logger.e(T, "Unexpected error while loading info: $e")
            }
        }

        val plugins: MutableSet<Plugin> = hashSetOf()
        for ((file: File, info: PluginInfo) in pluginInfos.values) {
            try {
                for (dependency in info.depend) {
                    if (!pluginInfos.containsKey(dependency)) {
                        throw DependencyNotFoundException("Unable to find plugin [$dependency] for plugin [${info.name}]")
                        //Logger.e(T, "Unable to find plugin [$dependency] for plugin ${config.fullName}")
                    }
                }

                if (onPluginLoad != null) onPluginLoad(info.name)
                val plugin = loadPlugin(info, file)
                if (!Version.fromString(info.apiVersion).isCompatibleWith(Info.PLUGINCORE_VERSION)) {
                    Logger.w(javaClass.simpleName, "Incompatible plugin version(${info.apiVersion}) found on plugin [${plugin.name}]")
                }
                plugins.add(plugin)
                plugin.onEnable()
            } catch (e: Exception) {
                Logger.e(T, e.toString())
                if (Session.isDebugging) e.printStackTrace()
            }
        }

        Session.pluginManager.plugins = plugins
        return plugins
    }

    private fun loadPlugin(info: PluginInfo, file: File): StarlightPlugin {
        val parent = file.parentFile
        val dataDir = File(parent, "${info.name}(${info.id})")

        if (dataDir.exists() && !dataDir.isDirectory) {
            throw InvalidPluginException(
                "Data folder for plugin ${info.name} already exists and is not a directory"
            )
        } else if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        Logger.v(T, "Loading plugin ${info.fullName}")
        val loader: PluginClassLoader
        try {
            loader = PluginClassLoader(this, javaClass.classLoader!!, info, dataDir, file)
            Logger.v(T, "Loaded plugin ${info.fullName} (${file.name})")
        } catch (e: InvalidPluginException) {
            Logger.e(T, "Failed to load plugin ${info.fullName} (${file.name}): $e")
            throw e
        }
        loaders[info.name] = loader
        val plugin = loader.plugin
        plugin.setConfigPath(File(plugin.getDataFolder(), "config-plugin.json"))
        loadAssets(file, plugin)
        if (plugin !in Session.pluginManager.plugins) {
            Session.pluginManager.plugins += plugin
        }
        return plugin
    }

    fun loadAssets(file: File, plugin: Plugin, force: Boolean = false) {
        require(plugin is StarlightPlugin) { "Plugin [${plugin.name}] does not extend StarlightPlugin" }
        var jar: JarFile? = null
        try {
            jar = JarFile(file)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() ?: break
                if (!entry.name.startsWith("assets/")) continue
                val fileName = entry.name.split("assets/").last()
                val writeFile = File(plugin.getDataFolder().resolve("assets/"), fileName)

                if (force || !writeFile.exists()) {
                    val entStream = jar.getInputStream(entry)
                    if (writeFile.isDirectory) {
                        writeFile.deleteRecursively()
                    }
                    writeFile.parentFile?.mkdirs()
                    writeFile.writeBytes(entStream.readBytes())
                    Logger.v(T, "Loaded asset [${fileName}] from plugin [${plugin.info.fullName}]")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw IllegalStateException("Failed to load asset")
        } finally {
            jar?.close()
        }
    }

    private fun loadInfoFile(file: File): PluginInfo {
        var jar: JarFile? = null
        var stream: InputStream? = null

        try {
            jar = JarFile(file)
            val ent: JarEntry = jar.getJarEntry("res/raw/starlight.json") ?: throw FileNotFoundException("Cannot find starlight.json")

            stream = jar.getInputStream(ent)
            return PluginInfo.decode(stream.readString())
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
            synchronized(loaders) {
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
        }
        return null
    }

    fun registerListener(plugin: Plugin, listener: EventListener) {
        val methods: MutableSet<Method>
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

    internal fun purge() {
        classes.clear()
        loaders.clear()
    }
}