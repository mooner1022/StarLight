/*
 * PluginLoader.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import android.os.Environment
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.logger.internal.Logger
import dev.mooner.starlight.plugincore.plugin.PluginDependency.Companion.VERSION_ANY
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.readString
import dev.mooner.starlight.plugincore.version.Version
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.util.jar.JarEntry
import java.util.jar.JarFile

private val LOG = LoggerFactory.logger {  }

class PluginLoader {
    private val classes: MutableMap<String, Class<*>> = hashMapOf()
    private val loaders: MutableMap<String, PluginClassLoader> = LinkedHashMap()

    @Suppress("DEPRECATION")
    private val defDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/")
    //private val dexDirectory = File(Environment.getExternalStorageDirectory(), "StarLight/plugins/.dex/")

    /*
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
                Logger.e(T, "Unexpected error while loading plugin info: $e")
            }
        }

        val plugins: MutableSet<StarlightPlugin> = hashSetOf()
        for ((file: File, info: PluginInfo) in pluginInfos.values) {
            try {
                for (dependency in info.dependency) {
                    if (dependency.pluginId !in pluginInfos) {
                        throw DependencyNotFoundException("Unable to find dependency '$dependency' for plugin [${info.name}]")
                        //Logger.e(T, "Unable to find plugin [$dependency] for plugin ${config.fullName}")
                    }
                    val pluginInfo = pluginInfos[dependency.pluginId]!!.second
                    if (dependency.supportedVersion != VERSION_ANY && Version.fromString(dependency.supportedVersion) incompatibleWith pluginInfo.version) {
                        Logger.w(javaClass.simpleName,"Incompatible dependency version(required: ${dependency.supportedVersion}, found: ${pluginInfo.version}) found on plugin: ${info.name}")
                    }
                }

                if (onPluginLoad != null) onPluginLoad(info.name)
                val plugin = loadPlugin(info, file)
                if (info.apiVersion incompatibleWith Info.PLUGINCORE_VERSION) {
                    Logger.w(javaClass.simpleName, "Incompatible plugin version(${info.apiVersion}) found on plugin: ${info.fullName}")
                    continue
                }
                plugins += plugin
                plugin.onEnable()
            } catch (e: Error) {
                Logger.e(T, e.toString())
                if (Session.isDebugging) e.printStackTrace()
            }
        }

        Session.pluginManager.plugins = plugins
        return plugins
    }
     */

    fun loadPlugins(dir: File = defDirectory): Flow<Any> =
        flow {
            if (!dir.exists() || !dir.isDirectory) {
                dir.mkdirs()
                emit(hashSetOf<StarlightPlugin>())
            }

            // TODO: Finish rewriting code
            /*
            val infoMap = (dir.listFiles() ?: emptyArray())
                .asFlow()
                .filter { file -> file.extension in arrayOf("apk", "jar") }
                .map { it to loadInfoFile(it) }
                .catch { e ->
                    when(e) {
                        is FileNotFoundException, is IllegalStateException ->
                            LOG.error(e)
                        else ->
                            LOG.error { "Unexpected error while loading plugin info: $e" }
                    }
                }
                .toList()
             */


            val pluginInfos: MutableMap<String, Pair<File, PluginInfo>> = hashMapOf()
            for (file in dir.listFiles { it -> it.extension in listOf("apk", "jar") }?: arrayOf()) {
                val info: PluginInfo
                try {
                    info = loadInfoFile(file)
                    pluginInfos[info.id] = Pair(file, info)
                } catch (e: FileNotFoundException) {
                    LOG.error(e)
                    //throw InvalidPluginException(e.toString())
                } catch (e: IllegalStateException) {
                    LOG.error(e)
                    //throw InvalidPluginException(e.toString())
                } catch (e: Exception) {
                    LOG.error { "Unexpected error while loading plugin info: $e" }
                }
            }

            val plugins: MutableSet<StarlightPlugin> = hashSetOf()
            for ((file: File, info: PluginInfo) in pluginInfos.values) {
                try {
                    for (dependency in info.dependency) {
                        if (dependency.pluginId !in pluginInfos) {
                            throw DependencyNotFoundException("Unable to find dependency '$dependency' for plugin [${info.name}]")
                            //Logger.e(T, "Unable to find plugin [$dependency] for plugin ${config.fullName}")
                        }
                        val pluginInfo = pluginInfos[dependency.pluginId]!!.second
                        if (dependency.supportedVersion != VERSION_ANY && Version.fromString(dependency.supportedVersion) incompatibleWith pluginInfo.version) {
                            LOG.warn { "Incompatible dependency version(required: ${dependency.supportedVersion}, found: ${pluginInfo.version}) found on plugin: ${info.name}" }
                        }
                    }

                    emit(info.name)
                    val plugin = loadPlugin(info, file)
                    if (info.apiVersion incompatibleWith Info.PLUGINCORE_VERSION) {
                        LOG.warn { "Incompatible plugin version(${info.apiVersion}) found on plugin: ${info.fullName}" }
                        continue
                    }
                    plugins += plugin
                    plugin.onEnable()
                } catch (e: Error) {
                    LOG.error(e)
                    if (Session.isDebugging)
                        e.printStackTrace()
                }
            }

            Session.pluginManager.plugins = plugins
            emit(plugins)
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

        LOG.verbose { 
            translate { 
                Locale.ENGLISH { "Loading plugin ${info.fullName}" }
                Locale.KOREAN  { "${info.fullName} 플러그인 로드중" }
            }
        }
        val loader: PluginClassLoader
        try {
            loader = PluginClassLoader(this, javaClass.classLoader!!, info, dataDir, file)
            LOG.verbose {
                translate {
                    Locale.ENGLISH { "Loaded plugin ${info.fullName} (${file.name})" }
                    Locale.KOREAN  { "${info.fullName} 플러그인 로드 성공" }
                }
            }
        } catch (e: InvalidPluginException) {
            LOG.error { "Failed to load plugin ${info.fullName} (${file.name}): $e" }
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

    private fun loadAssets(file: File, plugin: StarlightPlugin, force: Boolean = false) {
        var jar: JarFile? = null
        try {
            jar = JarFile(file)
            val entries = jar.entries()
            val parent = plugin.getDataFolder().resolve("assets/")
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() ?: break
                if (!entry.name.startsWith("assets/")) continue

                val fileName = entry.name.split("assets/").last()
                File(parent, fileName).apply {
                    if (force || !exists()) {
                        val entStream = jar.getInputStream(entry)
                        if (isDirectory) {
                            deleteRecursively()
                        }
                        parentFile?.mkdirs()
                        writeBytes(entStream.readBytes())
                        LOG.verbose { "Loaded asset [${fileName}] from plugin [${plugin.info.fullName}]" }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw IllegalStateException("Failed to load asset: $e")
        } finally {
            jar?.close()
        }
    }

    private fun loadInfoFile(file: File): PluginInfo {
        var jar: JarFile? = null
        var stream: InputStream? = null

        try {
            jar = JarFile(file)
            val ent: JarEntry = jar.getJarEntry("res/raw/starlight.json")
                ?: throw FileNotFoundException("Cannot find starlight.json")

            stream = jar.getInputStream(ent)
            return PluginInfo.decodeFromString(stream.readString())
        } catch (e: IOException) {
            throw IllegalStateException("Cannot open starlight.json: $e")
        } finally {
            jar?.close()
            stream?.close()
        }
    }

    fun getClass(name: String): Class<*>? {
        classes[name] ?.let { return it } ?: synchronized(loaders) {
            for ((_, loader) in loaders) {
                try {
                    return loader.findClass(name, false)
                } catch (_: ClassNotFoundException) {

                }
            }
        }
        return null
    }

    fun registerListener(plugin: StarlightPlugin, listener: EventListener) {
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
            LOG.error { "Error while adding listener ${listener.javaClass.simpleName}: NoClassDefFound" }
            return
        }

        for (method in methods) {
            //val annotation = method.getAnnotation(EventHandler::class.java) ?: continue
            val checkClass: Class<*> = method.parameterTypes[0]
            if (method.parameterTypes.size != 1 || !EventListener::class.java.isAssignableFrom(checkClass)) {
                LOG.error { "Attempted to register invalid listener: ${listener.javaClass.simpleName}" }
                continue
            }
            val eventClass = checkClass.asSubclass(EventListener::class.java)


            /*
             * UNFINISHED FUNCTION
             */
        }
    }

    fun setClass(name: String, clazz: Class<*>) {
        if (name !in classes) {
            classes[name] = clazz
        }
    }

    private fun removeClass(name: String) {
        if (name in classes) {
            classes -= name
        }
    }

    internal fun purge() {
        classes.clear()
        loaders.clear()
    }
}