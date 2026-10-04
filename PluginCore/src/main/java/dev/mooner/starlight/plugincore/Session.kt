/*
 * Session.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore

import dev.mooner.starlight.plugincore.api.ApiManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.language.LanguageManager
import dev.mooner.starlight.plugincore.library.LibraryLoader
import dev.mooner.starlight.plugincore.library.LibraryManager
import dev.mooner.starlight.plugincore.library.LibraryManagerApi
import dev.mooner.starlight.plugincore.logger.internal.Logger
import dev.mooner.starlight.plugincore.plugin.PluginContext
import dev.mooner.starlight.plugincore.plugin.PluginContextImpl
import dev.mooner.starlight.plugincore.plugin.PluginLoader
import dev.mooner.starlight.plugincore.plugin.PluginManager
import dev.mooner.starlight.plugincore.project.ProjectLoader
import dev.mooner.starlight.plugincore.project.ProjectManager
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.TranslationManager
import dev.mooner.starlight.plugincore.utils.NetworkUtil
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.widget.WidgetManager
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.properties.Delegates.notNull

object Session {

    @JvmStatic
    var state: InitState = InitState.None
        private set

    val isInitComplete
        get() = state == InitState.Done

    val json: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val languageManager : LanguageManager = LanguageManager()
    val pluginLoader    : PluginLoader    = PluginLoader()
    val pluginManager   : PluginManager   = PluginManager()
    val widgetManager   : WidgetManager   = WidgetManager()

    var projectManager: ProjectManager by notNull()
        private set
    var projectLoader: ProjectLoader by notNull()
        private set

    private var libraryLoader: LibraryLoader? = null
    var libraryManager: LibraryManager? = null
        private set

    var apiManager: ApiManager by notNull()
        private set

    fun init(locale: Locale, baseDir: File, codeCacheDir: File): PluginContext? {
        if (state != InitState.None) {
            Logger.w("Session", "Rejecting re-init of Session")
            return null
        }
        state = InitState.Processing

        val preStack = Thread.currentThread().stackTrace[2]
        if (!preStack.className.startsWith("dev.mooner.starlight")) {
            throw IllegalAccessException("Illegal access to internal function init() from $preStack")
        }

        TranslationManager.init(locale)

        val projectDir = File(baseDir, "projects/")
        projectManager = ProjectManager(projectDir)
        projectLoader  = ProjectLoader(projectDir)
        apiManager     = ApiManager()

        if (GlobalConfig.category("beta_features").getBoolean("load_external_dex_libs", false)) {
            libraryLoader  = LibraryLoader()
            libraryManager = LibraryManager(libraryLoader!!.loadLibraries(baseDir, codeCacheDir).toMutableSet())
            apiManager.addApi(LibraryManagerApi())
        }
        state = InitState.Done

        return PluginContextImpl(
            "starlight",
            "StarLight",
            getStarLightDirectory().path
        )
    }

    fun shutdown() {
        val preStack = Thread.currentThread().stackTrace[2]
        if (!preStack.className.startsWith("dev.mooner.starlight")) {
            throw IllegalAccessException("Illegal access to internal function shutdown()")
        }

        //globalConfig.push()

        NetworkUtil.purge()
        apiManager.purge()

        languageManager.purge()
        widgetManager.purge()
        pluginLoader.purge()
        pluginManager.purge()
        projectManager.purge()
    }

    sealed class InitState {

        data object None       : InitState()
        data object Processing : InitState()
        data object Done       : InitState()
    }
}