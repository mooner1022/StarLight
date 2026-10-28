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
import dev.mooner.starlight.plugincore.plugin.arch.Arch
import dev.mooner.starlight.plugincore.plugin.arch.getArch
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.copyAsReadOnly
import dev.mooner.starlight.plugincore.utils.errorTranslated
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.utils.warnTranslated
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.util.jar.JarFile

private val logger = LoggerFactory.logger {  }

class PluginLoader {
    private val classes: MutableMap<String, Class<*>> = hashMapOf()
    private val loaders: MutableMap<String, PluginClassLoader> = LinkedHashMap()

    private val defDirectory = File(getStarLightDirectory(), "plugins/")

    fun loadPlugins(context: Context, dir: File = defDirectory): Flow<Any> =
        flow {
            val entries: MutableMap<File, InstalledPlugin> = linkedMapOf()
            val candidates = scan(dir, entries)

            val resolution = PluginResolver.resolve(
                candidates.mapValues { (_, value) -> value.second },
                Info.PLUGINCORE_VERSION,
                Session.pluginManager.getDisabledIds()
            )
            for ((id, exclusion) in resolution.excluded) {
                val (file, info) = candidates[id]!!
                logExclusion(info, exclusion)
                entries[file] = InstalledPlugin(file, info, exclusion.toState())
            }

            Session.pluginManager.purge()

            logger.verbose {
                "Plugin load priority\n" + resolution.order
                    .withIndex()
                    .joinToString("\n") { "#${it.index} - ${it.value}" }
            }

            for (id in resolution.order) {
                val (file, info) = candidates[id]!!
                emit(info.name)

                entries[file] = try {
                    val plugin = loadPlugin(context, file, info)

                    Session.pluginManager.addPlugin(plugin)
                    plugin.getListeners().forEach(EventListener::onEnable)
                    InstalledPlugin(file, info, PluginState.Loaded(plugin))
                } catch (e: Throwable) {
                    logger.error(e)
                    if (Info.DEBUG)
                        e.printStackTrace()
                    InstalledPlugin(file, info, PluginState.Failed(e))
                }
            }

            context.codeCacheDir.resolve("plugins")
                .listFiles { file -> file.name !in candidates }
                ?.forEach(File::deleteRecursively)

            Session.pluginManager.setInstalled(entries.values.toList())
            emit(Session.pluginManager.plugins)
        }

    fun scanPlugins(dir: File = defDirectory) {
        val entries: MutableMap<File, InstalledPlugin> = linkedMapOf()
        val disabled = Session.pluginManager.getDisabledIds()
        for ((id, value) in scan(dir, entries)) {
            val (file, info) = value
            entries[file] = InstalledPlugin(file, info, if (id in disabled) PluginState.Disabled else PluginState.SafeMode)
        }
        Session.pluginManager.setInstalled(entries.values.toList())
    }

    private fun scan(dir: File, entries: MutableMap<File, InstalledPlugin>): Map<String, Pair<File, PluginInfo>> {
        if (!dir.isDirectory)
            dir.mkdirs()

        val found: MutableMap<String, MutableList<Pair<File, PluginInfo>>> = linkedMapOf()
        val files = dir.listFiles { file -> file.isFile && file.extension in SUPPORTED_EXT }
            ?.sortedBy(File::getName)
            .orEmpty()
        for (file in files) {
            try {
                val info = PluginInstaller.readPluginInfo(file)
                found.getOrPut(info.id) { arrayListOf() } += file to info
            } catch (e: Exception) {
                logger.error { "Failed to read plugin info from ${file.name}: $e" }
                entries[file] = InstalledPlugin(file, null, PluginState.Invalid(e))
            }
        }

        return found.mapValues { (id, list) ->
            val kept = list.maxWith(
                compareBy<Pair<File, PluginInfo>> { it.second.version }
                    .thenBy { it.first.name == PluginInstaller.fileNameOf(id) }
                    .thenBy { it.first.lastModified() }
            )
            for (other in list) {
                if (other === kept)
                    continue
                val exclusion = PluginExclusion.DuplicateId(kept.first)
                logExclusion(other.second, exclusion)
                entries[other.first] = InstalledPlugin(other.first, other.second, PluginState.Excluded(exclusion))
            }
            kept
        }
    }

    private fun PluginExclusion.toState(): PluginState =
        if (this == PluginExclusion.Disabled)
            PluginState.Disabled
        else
            PluginState.Excluded(this)

    private fun logExclusion(info: PluginInfo, exclusion: PluginExclusion) {
        when (exclusion) {
            PluginExclusion.Disabled ->
                logger.info { "Skipping disabled plugin: ${info.fullName}" }
            is PluginExclusion.DuplicateId ->
                logger.warnTranslated {
                    Locale.ENGLISH { "Plugin ${info.fullName} was skipped because ${exclusion.kept.name} has the same id." }
                    Locale.KOREAN  { "같은 id를 가진 ${exclusion.kept.name} 파일이 있어 플러그인 ${info.fullName}을(를) 건너뛰었어요." }
                }
            is PluginExclusion.IncompatibleApi ->
                logger.warnTranslated {
                    Locale.ENGLISH { "Incompatible PluginCore version(${exclusion.apiVersion}) found on plugin: ${info.fullName}" }
                    Locale.KOREAN  { "현재 버전 (${Info.PLUGINCORE_VERSION})과 호환되지 않는 PluginCore 의존성(${exclusion.apiVersion}) 발견: 플러그인 ${info.fullName}" }
                }
            is PluginExclusion.MissingDependency ->
                logger.errorTranslated {
                    Locale.ENGLISH { "Required dependency ${exclusion.dependency} for plugin ${info.fullName} wasn't found." }
                    Locale.KOREAN  { "플러그인 ${info.fullName}에 필요한 종속성 ${exclusion.dependency} 을(를) 찾을 수 없습니다." }
                }
            is PluginExclusion.ExcludedDependency ->
                logger.errorTranslated {
                    Locale.ENGLISH { "Required dependency ${exclusion.dependency} for plugin ${info.fullName} couldn't be loaded." }
                    Locale.KOREAN  { "플러그인 ${info.fullName}에 필요한 종속성 ${exclusion.dependency} 을(를) 로드할 수 없습니다." }
                }
            is PluginExclusion.IncompatibleDependency ->
                logger.errorTranslated {
                    Locale.ENGLISH { "Incompatible dependency version(required: ${exclusion.dependency.supportedVersion}, found: ${exclusion.found}) found on plugin: ${info.name}" }
                    Locale.KOREAN  { "요구된 버전과 다른 종속성 버전(required: ${exclusion.dependency.supportedVersion}, found: ${exclusion.found})이 발견되었습니다. 플러그인: ${info.name}" }
                }
            PluginExclusion.CircularDependency ->
                logger.errorTranslated {
                    Locale.ENGLISH { "Circular dependency found on plugin: ${info.fullName}" }
                    Locale.KOREAN  { "순환 종속성이 발견되었습니다. 플러그인: ${info.fullName}" }
                }
        }
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

        logger.info {
            translate { 
                Locale.ENGLISH { "Loading plugin ${info.fullName}" }
                Locale.KOREAN  { "${info.fullName} 플러그인 로드중" }
            }
        }
        val loader: PluginClassLoader
        try {
            val nativeLibDir =
                if (info.usesNativeLibrary)
                    loadNativeLibrary(info, file, context.filesDir)
                else null

            val dexFile = file.copyAsReadOnly(context.codeCacheDir.resolve("plugins/${info.id}"))

            val parentLoader: ClassLoader = info.customClassLoader
                ?.let { retrieveCustomClassLoader(it, dexFile.path) }
                ?: javaClass.classLoader!!

            loader = PluginClassLoader(context, parentLoader, dexFile, nativeLibDir?.path, this, info, file)
            logger.info {
                translate {
                    Locale.ENGLISH { "Loaded plugin ${info.fullName} (${file.name})" }
                    Locale.KOREAN  { "${info.fullName} 플러그인 로드 성공" }
                }
            }
        } catch (e: InvalidPluginException) {
            logger.error { "Failed to load plugin ${info.fullName} (${file.name}): $e" }
            throw e
        }
        loaders[info.id] = loader
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

    fun getClass(name: String): Class<*>? {
        return classes[name]
            ?: synchronized(loaders) {
                for ((_, loader) in loaders) {
                    try {
                        return@synchronized loader.findClass(name, false)
                    } catch (_: ClassNotFoundException) { }
                }
                null
            }
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
            arrayOf(/*"apk", "jar", "aar", */"slp")

        val PRESERVED_IDS =
            arrayOf("global", "starlight", "system", "core")
    }
}