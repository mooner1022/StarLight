package com.mooner.starlight.plugincore.plugin

import android.os.Environment
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.annotations.StarLightEventListener
import com.mooner.starlight.plugincore.event.Listener
import com.mooner.starlight.plugincore.utils.Utils.Companion.readString
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarFile

class PluginLoader {
    private val T = "PluginLoader"
    private val classes: HashMap<String, Class<*>> = HashMap()
    private val loaders: LinkedHashMap<String, PluginClassLoader> = LinkedHashMap()
    private val method: Method = URLClassLoader::class.java
        .getDeclaredMethod("addURL", URL::class.java)

    private val defDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/")
    //private val dexDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/.dex/")

    init {
        method.isAccessible = true
    }

    fun loadPlugins(dir: File = defDirectory): List<Plugin> {
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }

        val list: MutableList<Plugin> = mutableListOf()

        for (file in dir.listFiles { it -> it.name.substringAfterLast(".") == "apk" }?: arrayOf()) {
            try {
                val config: PluginConfig
                try {
                    config = getConfigFile(file)
                } catch (e: FileNotFoundException) {
                    Session.getLogger().e(T, e.toString())
                    throw InvalidPluginException(e.toString())
                } catch (e: IllegalStateException) {
                    Session.getLogger().e(T, e.toString())
                    throw InvalidPluginException(e.toString())
                }
                val plugin = loadPlugin(config, file)
                list.add(plugin)
                //PluginManager.plugins[config.name] = plugin
            } catch (e: Exception) {
                Session.getLogger().e(T, e.toString())
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

        Session.getLogger().i(T, "Loading plugin ${config.fullName}")
        val loader: PluginClassLoader
        try {
            loader = PluginClassLoader(this, javaClass.classLoader!!, config, dataDir, file)
            Session.getLogger().i(T, "Loaded plugin ${config.fullName} (${file.name})")
        } catch (e: InvalidPluginException) {
            Session.getLogger().e(T, "Failed to load plugin ${config.fullName} (${file.name}): $e")
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
            val ent: JarEntry = jar.getJarEntry("starlight.json") ?: throw FileNotFoundException("Cannot find starlight.json")

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

    fun registerListener(plugin: Plugin, listener: Listener) {
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
            Session.getLogger().e("PluginLoader", "Error while adding listener ${listener.javaClass.simpleName}: NoClassDefFound")
            return
        }

        for (method in methods) {
            val annotation = method.getAnnotation(StarLightEventListener::class.java) ?: continue

            val checkClass: Class<*> = method.parameterTypes[0]
            if (method.parameterTypes.size != 1 || !Listener::class.java.isAssignableFrom(checkClass)) {
                Session.getLogger().e("PluginLoader", "Attempted to register invalid listener: ${listener.javaClass.simpleName}")
                continue
            }
            val eventClass = checkClass.asSubclass(Listener::class.java)
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
        val clazz = classes.remove(name)
    }
}