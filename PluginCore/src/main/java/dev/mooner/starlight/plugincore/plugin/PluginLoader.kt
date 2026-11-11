/*
 * PluginLoader.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import android.content.Context
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
    private val loaders: MutableMap<String, PluginHostClassLoader> = LinkedHashMap()

    private val defDirectory = File(getStarLightDirectory(), "plugins/")

    fun loadPlugins(context: Context, dir: File = defDirectory): Flow<Any> =
        flow {
            if (!PluginCodeLoader.isSupported) {
                logger.info { "Plugins aren't loaded in this build" }
                emit(Session.pluginManager.plugins)
                return@flow
            }
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

            /** The plugins that failed as they were loaded: the ones that need them aren't loaded either. */
            val failed = hashSetOf<String>()
            for (id in resolution.order) {
                val (file, info) = candidates[id]!!
                emit(info.name)

                // A plugin that needs one that just failed would run against something that isn't there. The
                // order has each plugin after the ones it depends on.
                val missing = info.dependency.firstOrNull { !it.isOptional && it.pluginId in failed }
                if (missing != null) {
                    val exclusion = PluginExclusion.ExcludedDependency(missing)
                    logExclusion(info, exclusion)
                    failed += info.id
                    entries[file] = InstalledPlugin(file, info, exclusion.toState())
                    continue
                }

                val before = RegistrationSnapshot.take()
                var plugin: StarlightPlugin? = null
                entries[file] = try {
                    plugin = loadPlugin(context, file, info)

                    Session.pluginManager.addPlugin(plugin)
                    // setEnabled(true) would call onEnable on the plugin only; loading enables every listener.
                    plugin.enableOnLoad()
                    InstalledPlugin(file, info, PluginState.Loaded(plugin))
                } catch (e: Throwable) {
                    failed += info.id
                    unloadFailed(plugin, info, before)
                    // Which plugin, and what was behind the error: the error alone, as it used to be logged,
                    // said neither. One that isn't a plugin at all has been reported where it was found out.
                    if (e !is InvalidPluginException)
                        logger.error {
                            "Failed to load plugin ${info.fullName} (${file.name}): $e" +
                                generateSequence(e.cause) { it.cause }.take(MAX_CAUSES).joinToString("") { "\ncaused by: $it" }
                        }
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

    /**
     * Undoes what loading [plugin] did before it failed. It is shown as failed, and nothing of it is left in use:
     * it used to stay among the loaded plugins, called for network changes and links and handed to scripts, with
     * whatever it had registered before it threw.
     */
    private fun unloadFailed(plugin: StarlightPlugin?, info: PluginInfo, before: RegistrationSnapshot) {
        if (plugin != null) {
            // Its listeners that were enabled are told it is over; each is guarded there.
            runCatching { plugin.disableOnUnload() }
            Session.pluginManager.dropFailed(plugin)
        }
        loaders.remove(info.id)
        val removed = runCatching { before.removeNewer() }.getOrDefault(emptyList())
        if (removed.isNotEmpty())
            logger.info { "Took out what plugin ${info.fullName} registered before it failed: ${removed.joinToString()}" }
    }

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
                    when (val reason = exclusion.reason) {
                        is ApiCompatibility.RequiresNewerApp -> {
                            Locale.ENGLISH { "Plugin ${info.fullName} requires PluginCore ${reason.required} or newer (current ${reason.current})." }
                            Locale.KOREAN  { "플러그인 ${info.fullName}은(는) PluginCore ${reason.required} 이상이 필요해요. (현재 ${reason.current})" }
                        }
                        is ApiCompatibility.Outdated -> {
                            Locale.ENGLISH { "Plugin ${info.fullName} targets PluginCore ${reason.apiVersion}, older than the supported ${reason.minimum}." }
                            Locale.KOREAN  { "플러그인 ${info.fullName}은(는) 지원 범위(${reason.minimum} 이상)보다 오래된 PluginCore ${reason.apiVersion}용이에요." }
                        }
                        ApiCompatibility.Compatible -> {
                            Locale.ENGLISH { "Plugin ${info.fullName} was excluded for its PluginCore version ${exclusion.apiVersion}." }
                            Locale.KOREAN  { "플러그인 ${info.fullName}이(가) PluginCore 버전(${exclusion.apiVersion}) 때문에 제외됐어요." }
                        }
                    }
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
        val loader: PluginHostClassLoader
        try {
            val nativeLibDir =
                if (info.usesNativeLibrary)
                    loadNativeLibrary(info, file, context.filesDir)
                else null

            val dexFile = file.copyAsReadOnly(context.codeCacheDir.resolve("plugins/${info.id}"))

            val parentLoader: ClassLoader = info.customClassLoader
                ?.let { PluginCodeLoader.createCustomClassLoader(it, dexFile.path, javaClass.classLoader!!) }
                ?: javaClass.classLoader!!

            loader = PluginCodeLoader.createClassLoader(context, parentLoader, dexFile, nativeLibDir?.path, this, info, file)
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

        // A plugin's own folder is kept in step with the plugin file: an update gets its new libraries. The folder
        // the V8 plugin shares with the app keeps being filled as before, a file where there is none.
        if (info.id != "v8") {
            try {
                if (PluginFiles.unpackNativeLibs(file, libsDir, pathPrefix))
                    logger.verbose { "Unpacked the native libraries of plugin [${info.fullName}]" }
            } catch (e: IOException) {
                e.printStackTrace()
                throw IllegalStateException("Failed to load native libraries: $e")
            }
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

    /**
     * Unpacks the plugin's assets into its data folder, again whenever the plugin file or its asset revision
     * changed. They used to follow the revision alone, so a version whose author forgot to raise it kept the old
     * assets, its icon among them, and one whose unpacking was cut short was never unpacked again.
     */
    private fun loadAssets(file: File, plugin: StarlightPlugin) {
        try {
            val target = plugin.getInternalDataDirectory().resolve("assets")
            if (PluginFiles.unpackAssets(file, target, plugin.info.assetRevision))
                logger.debug { "Unpacked the assets of plugin [${plugin.info.fullName}]" }
        } catch (e: IOException) {
            e.printStackTrace()
            throw IllegalStateException("Failed to load asset: $e")
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

    internal fun purge() {
        classes.clear()
        loaders.clear()
    }

    companion object {
        /** How many causes of a load failure its log names. */
        private const val MAX_CAUSES = 4

        val SUPPORTED_EXT =
            arrayOf(/*"apk", "jar", "aar", */"slp")

        val PRESERVED_IDS =
            arrayOf("global", "starlight", "system", "core")
    }
}