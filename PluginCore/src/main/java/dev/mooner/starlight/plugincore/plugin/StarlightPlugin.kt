/*
 * StarlightPlugin.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import android.view.View
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.config.data.Config
import dev.mooner.starlight.plugincore.config.data.ConfigImpl
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.fireEvent
import dev.mooner.starlight.plugincore.utils.getFileSize
import dev.mooner.starlight.plugincore.widget.Widget
import kotlinx.serialization.decodeFromString
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.pathString

abstract class StarlightPlugin: EventListener {

    internal lateinit var file: File
    private lateinit var dataDir: File
    private lateinit var classLoader: ClassLoader
    private var isEnabled = false
    private var configPath: File? = null
    lateinit var info: PluginInfo

    val fileSize: Float get() = file.getFileSize()
    val fileName: String get() = file.name

    companion object {
        private const val T = "StarlightPlugin"
    }

    constructor() {
        val classLoader = this.javaClass.classLoader
        if (classLoader !is PluginClassLoader) {
            throw IllegalStateException("StarlightPlugin requires ${PluginClassLoader::class.java.name}")
        }
        classLoader.initialize(this)
    }

    protected constructor(
        config: PluginInfo,
        dataDir: File,
        file: File,
    ) {
        val classLoader = this.javaClass.classLoader
        if (classLoader is PluginClassLoader) {
            throw IllegalStateException("Cannot use initialization constructor at runtime")
        }
        init(config, dataDir, file, classLoader!!)
    }

    open val configObjects: List<CategoryConfigObject> = listOf()

    @Deprecated(
        message = "Retained for legacy compatability, don't use it.",
        replaceWith = ReplaceWith("onConfigUpdated(config: Config)", "dev.mooner.starlight.plugincore.plugin.onConfigUpdated")
    )
    open fun onConfigUpdated(updated: Map<String, Any>) {}

    open fun onConfigUpdated(config: Config, updated: Set<String>) {}

    open fun onConfigChanged(id: String, view: View?, data: Any) {}

    open fun isEnabled(): Boolean = isEnabled

    protected fun setEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            if (isEnabled) {
                onEnable()
            } else {
                onDisable()
            }
        }
    }

    internal fun setConfigPath(path: File) {
        configPath = path
    }

    protected fun getPluginConfig(): Config {
        return if (configPath == null || !configPath!!.isFile || !configPath!!.exists()) ConfigImpl(emptyMap()) else {
            val loadedMap: Map<String, Map<String, TypedString>> = Session.json.decodeFromString(configPath!!.readText())
            ConfigImpl(loadedMap)
        }
    }

    fun getDataFolder(): File = dataDir

    fun getAsset(directory: String): File = File(dataDir.resolve("assets"), directory)

    protected fun getClassLoader(): ClassLoader = classLoader

    protected fun addLanguage(language: Language) {
        var isLoadSuccess = false
        try {
            Session.languageManager.addLanguage(Path(dataDir.resolve("assets").path, language.id).pathString, language)
            isLoadSuccess = true
        } catch (e: IllegalStateException) {
            Logger.e(T, e.toString())
            e.printStackTrace()
        } finally {
            Logger.i(T,(if (isLoadSuccess) "Successfully added" else "Failed to add") + " language ${language.name}")
        }
    }

    protected fun addWidget(widget: Widget) =
        Session.widgetManager.addWidget(this.info.name, widget)

    protected fun <T> addApi(api: Api<T>) =
        Session.apiManager.addApi(api)

    protected inline fun <reified T: ProjectEvent> fireProjectEvent(vararg args: Any, noinline onFailure: (project: Project, e: Throwable) -> Unit = { _, _ -> }) =
        Session.projectManager.fireEvent<T>(args, onFailure = onFailure)

    override fun toString(): String = info.fullName

    override fun equals(other: Any?): Boolean {
        return when(other) {
            is StarlightPlugin -> other.info.id == this.info.id
            null -> false
            else -> false
        }
    }

    fun init(
        info: PluginInfo,
        dataDir: File,
        file: File,
        classLoader: ClassLoader
    ) {
        this.info = info
        this.dataDir = dataDir
        this.file = file
        this.classLoader = classLoader
    }

    override fun hashCode(): Int {
        var result = dataDir.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + info.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + configObjects.hashCode()
        return result
    }
}