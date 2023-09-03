/*
 * PluginLoader.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import android.content.Context
import dalvik.system.PathClassLoader
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.plugin.PluginDependency.Companion.VERSION_ANY
import dev.mooner.starlight.plugincore.plugin.arch.Arch
import dev.mooner.starlight.plugincore.plugin.arch.getArch
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.errorTranslated
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.utils.readString
import dev.mooner.starlight.plugincore.utils.warnTranslated
import dev.mooner.starlight.plugincore.version.Version
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.util.jar.JarEntry
import java.util.jar.JarFile

private val logger = LoggerFactory.logger {  }

class PluginLoader {
    private val classes: MutableMap<String, Class<*>> = hashMapOf()
    private val loaders: MutableMap<String, PluginClassLoader> = LinkedHashMap()

    private val defDirectory = File(getStarLightDirectory(), "plugins/")
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

    fun loadPlugins(context: Context, dir: File = defDirectory): Flow<Any> =
        flow {
            if (!dir.exists() || !dir.isDirectory) {
                dir.mkdirs()
                emit(emptySet<StarlightPlugin>())
            }

            val pluginFiles : MutableMap<String, Pair<File, PluginInfo>> = hashMapOf()
            val loadPriority: MutableMap<String, Int> = hashMapOf()

            for ((index, file) in (dir.listFiles { it -> it.extension in SUPPORTED_EXT } ?: arrayOf()).withIndex()) {
                val info: PluginInfo
                try {
                    info = loadInfoFile(file)
                    if (info.id.trim().lowercase() in PRESERVED_IDS)
                        throw IllegalArgumentException("Preserved or unusable plugin id: ${info.id}")
                    pluginFiles [info.id] = file to info
                    loadPriority[info.id] = index
                } catch (e: FileNotFoundException) {
                    logger.error(e)
                    //throw InvalidPluginException(e.toString())
                } catch (e: IllegalStateException) {
                    logger.error(e)
                    //throw InvalidPluginException(e.toString())
                } catch (e: Exception) {
                    logger.error { "Unexpected error while loading plugin info: $e" }
                }
            }

            for ((_, info) in pluginFiles.values) {
                if (info.dependency.isEmpty())
                    continue

                for (dependency in info.dependency) {
                    if (info.apiVersion incompatibleWith Info.PLUGINCORE_VERSION) {
                        logger.warn { "Incompatible PluginCore version(${info.apiVersion}) found on plugin: ${info.fullName}" }
                        continue
                    }
                    if (dependency.pluginId !in pluginFiles) {
                        logger.errorTranslated {
                            Locale.ENGLISH { "Required dependency $dependency for plugin ${info.fullName} wasn't found." }
                            Locale.KOREAN  { "플러그인 ${info.fullName}에 필요한 종속성 $dependency 을(를) 찾을 수 없습니다." }
                        }
                        continue
                    }
                    if (dependency.supportedVersion != VERSION_ANY &&
                        Version.fromString(dependency.supportedVersion) incompatibleWith info.version) {
                        logger.warnTranslated {
                            Locale.ENGLISH { "Incompatible dependency version(required: ${dependency.supportedVersion}, found: ${info.version}) found on plugin: ${info.name}" }
                            Locale.KOREAN  { "요구된 버전과 다른 종속성 버전(required: ${dependency.supportedVersion}, found: ${info.version})이 발견되었습니다. 플러그인: ${info.name}" }
                        }
                    }
                    logger.verbose { "${info.id} <= $dependency" }
                    loadPriority[info.id] = loadPriority[info.id]!! + loadPriority[dependency.pluginId]!!
                }
            }

            //val plugins: MutableSet<StarlightPlugin> = hashSetOf()
            Session.pluginManager.purge()

            val sortedIds = loadPriority.toList().sortedBy { it.second }
            logger.verbose { sortedIds.withIndex().joinToString("\n") { "#${it.index} - ${it.value.first}" } }

            for ((id, _) in sortedIds) {
                val (file, info) = pluginFiles[id]!!
                emit(info.name)

                try {
                    val plugin = loadPlugin(context, file, info)

                    //plugins += plugin
                    Session.pluginManager.addPlugin(plugin)
                    plugin.getListeners().forEach(EventListener::onEnable)
                } catch (e: Throwable) {
                    logger.error(e)
                    if (Session.isDebugging)
                        e.printStackTrace()
                }
            }

            //plugins.forEach(Session.pluginManager::addPlugin)
            emit(Session.pluginManager.plugins)
        }

    private fun loadPlugin(context: Context, file: File, info: PluginInfo): StarlightPlugin {
        val parent = file.parentFile
        val dataDir = File(parent, "${info.name}(${info.id})")

        if (dataDir.exists() && !dataDir.isDirectory) {
            throw InvalidPluginException(
                "Data folder for plugin ${info.name} already exists and is not a directory"
            )
        } else if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        logger.verbose {
            translate { 
                Locale.ENGLISH { "Loading plugin ${info.fullName}" }
                Locale.KOREAN  { "${info.fullName} 플러그인 로드중" }
            }
        }
        val loader: PluginClassLoader
        try {
            val nativeLibDir = if (info.usesNativeLibrary)
                loadNativeLibrary(info, file, context.filesDir)
            else
                null
            val parentLoader: ClassLoader = info.customClassLoader
                ?.let { retrieveCustomClassLoader(it, file.path) }
                ?: javaClass.classLoader!!

            loader = PluginClassLoader(context, parentLoader, nativeLibDir?.path, this, info, file)
            logger.verbose {
                translate {
                    Locale.ENGLISH { "Loaded plugin ${info.fullName} (${file.name})" }
                    Locale.KOREAN  { "${info.fullName} 플러그인 로드 성공" }
                }
            }
        } catch (e: InvalidPluginException) {
            logger.error { "Failed to load plugin ${info.fullName} (${file.name}): $e" }
            throw e
        }
        loaders[info.name] = loader
        val plugin = loader.plugin

        loadAssets(file, plugin)

        if (plugin !in Session.pluginManager.plugins) {
            Session.pluginManager.addPlugin(plugin)
        }
        return plugin
    }

    private fun retrieveCustomClassLoader(name: String, dexPath: String): ClassLoader {
        val tempClassLoader = PathClassLoader(dexPath, javaClass.classLoader!!)
        val clazz = Class.forName(name, false, tempClassLoader)
        val primaryConstructor = clazz.constructors.find { cn ->
            cn.parameterTypes.size == 1 && ClassLoader::class.java.isAssignableFrom(cn.parameterTypes[0])
        } ?: clazz.getConstructor()

        return if (primaryConstructor.parameterTypes.isNotEmpty())
            primaryConstructor.newInstance(javaClass.classLoader!!) as ClassLoader
        else
            primaryConstructor.newInstance() as ClassLoader
    }

    private fun loadNativeLibrary(info: PluginInfo, file: File, parentDir: File, force: Boolean = false): File {
        val libsDir = if (info.id == "v8") {
            parentDir.resolve("lib/")
        } else {
            parentDir.resolve("plugin_data/${info.id}/libs/").also {
                if (!it.exists())
                    it.mkdirs()
            }
        }

        val pathPrefix = when(getArch()) {
            Arch.X86_32   -> "x86"
            Arch.X86_64   -> "x86_64"
            Arch.ARM_32   -> "armeabi"
            Arch.AARCH_64 -> "arm64"
            else -> null
        }
        if (pathPrefix == null) {
            logger.error { "Failed to resolve native library path prefix of arch: ${System.getProperty("os.arch")}" }
            return libsDir
        }

        var jar: JarFile? = null
        try {
            jar = JarFile(file)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() ?: break
                if (!entry.name.startsWith("lib/$pathPrefix")) continue

                val fileName = entry.name
                    .split("lib/$pathPrefix")
                    .last()
                    .split(File.separator)
                    .drop(1)
                    .joinToString(File.separator)

                logger.verbose { fileName }
                File(libsDir, fileName).apply {
                    if (!exists() || force) {
                        val entStream = jar.getInputStream(entry)
                        if (isDirectory) {
                            deleteRecursively()
                        }
                        parentFile?.mkdirs()
                        writeBytes(entStream.readBytes())
                        logger.verbose { "Loaded native library [${fileName}] from plugin [${info.fullName}]" }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw IllegalStateException("Failed to load native libraries: $e")
        } finally {
            jar?.close()
        }

        return libsDir
    }

    private fun loadAssets(file: File, plugin: StarlightPlugin, force: Boolean = false) {
        var jar: JarFile? = null
        try {
            val parent = plugin.getInternalDataDirectory()
                .resolve("assets")
                .also { it.mkdirs() }
            val assetVersion = runCatching { parent.resolve(".VERSION").readText().toInt() }
                .getOrDefault(-1)
            if (plugin.info.assetRevision == assetVersion)
                return
            logger.debug { "Found new version of asset($assetVersion -> ${plugin.info.assetRevision}), loading assets..." }
            parent.apply {
                deleteRecursively()
                mkdirs()
                resolve(".VERSION")
                    .writeText(plugin.info.assetRevision.toString())
            }

            jar = JarFile(file)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() ?: break
                if (!entry.name.startsWith("assets/")) continue

                val fileName = entry.name.split("assets/")
                    .drop(1)
                    .let {
                        if (it.size == 1)
                            it[0]
                        else
                            it.joinToString("assets/")
                    }
                File(parent, fileName).apply {
                    if (force || !exists()) {
                        val entStream = jar.getInputStream(entry)
                        if (isDirectory) {
                            deleteRecursively()
                        }
                        parentFile?.mkdirs()
                        writeBytes(entStream.readBytes())
                        logger.verbose { "Loaded asset [${fileName}] from plugin [${plugin.info.fullName}]" }
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
                } catch (_: ClassNotFoundException) { }
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
            logger.error { "Error while adding listener ${listener.javaClass.simpleName}: NoClassDefFound" }
            return
        }

        for (method in methods) {
            //val annotation = method.getAnnotation(EventHandler::class.java) ?: continue
            val checkClass: Class<*> = method.parameterTypes[0]
            if (method.parameterTypes.size != 1 || !EventListener::class.java.isAssignableFrom(checkClass)) {
                logger.error { "Attempted to register invalid listener: ${listener.javaClass.simpleName}" }
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

    companion object {
        val SUPPORTED_EXT =
            arrayOf("apk", "jar", "aar", "slp")

        val PRESERVED_IDS =
            arrayOf("global", "starlight", "system")
    }
}