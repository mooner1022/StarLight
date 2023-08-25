/*
 * StarlightPlugin.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import android.view.View
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.config.*
import dev.mooner.starlight.plugincore.config.data.ConfigData
import dev.mooner.starlight.plugincore.config.data.DataMap
import dev.mooner.starlight.plugincore.config.data.InMemoryConfig
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventBuilder
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.fireEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.getFileSize
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.utils.infoTranslated
import dev.mooner.starlight.plugincore.utils.isValidFile
import dev.mooner.starlight.plugincore.widget.Widget
import kotlinx.serialization.decodeFromString
import java.io.File

private val logger = LoggerFactory.logger {  }

@Suppress("unused")
abstract class StarlightPlugin: Plugin, EventListener {

    internal lateinit var originalFile: File
    private lateinit var internalDir: File

    private var isEnabled = false
    private val listeners: MutableSet<EventListener> = hashSetOf()

    private lateinit var classLoader: ClassLoader
    private lateinit var mInfo: PluginInfo
    override val info: PluginInfo
        get() = mInfo

    val context: PluginContext by lazy {
        PluginContextImpl(info.id, info.name, getInternalDataDirectory().path)
    }

    val fileSize: Float
        get() = originalFile.getFileSize()
    val fileName: String
        get() = originalFile.name

    constructor() {
        val classLoader = this.javaClass.classLoader
        if (classLoader !is PluginClassLoader) {
            throw IllegalStateException("StarlightPlugin requires ${PluginClassLoader::class.java.name}")
        }
        classLoader.initialize(this)
    }

    protected constructor(
        config: PluginInfo,
        internalDir: File,
        file: File,
    ) {
        val classLoader = this.javaClass.classLoader
        if (classLoader is PluginClassLoader) {
            throw IllegalStateException("Cannot use initialization constructor at runtime")
        }
        init(config, file, internalDir, classLoader!!)
    }

    override fun getConfigStructure(): ConfigStructure { return emptyList() }

    @Deprecated(
        message = "Retained for legacy compatability, don't use it.",
        replaceWith = ReplaceWith("onConfigUpdated(config: Config)", "dev.mooner.starlight.plugincore.plugin.onConfigUpdated")
    )
    override fun onConfigUpdated(updated: Map<String, Any>) {}

    override fun onConfigUpdated(config: ConfigData, updated: Map<String, Set<String>>) {}

    override fun onConfigChanged(id: String, view: View?, data: Any) {}

    override fun isEnabled(): Boolean = isEnabled

    protected fun addListener(listener: EventListener) {
        listeners += listener
        if (isEnabled)
            listener.onEnable()
    }

    /**
     * Returns all event listeners associated with this plugin, including the plugin itself.
     *
     * @return Set of event listeners
     */
    fun getListeners(): Set<EventListener> =
        listeners + this

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

    protected fun getPluginConfig(): ConfigData {
        val configPath = getExternalDataDirectory().resolve("config-plugin.json")
        return if (configPath.isValidFile) {
            configPath.readText()
                .let<_, DataMap>(Session.json::decodeFromString)
                .let(::InMemoryConfig)
        } else {
            InMemoryConfig(emptyMap())
        }
    }

    override fun getInternalDataDirectory(): File =
        internalDir
            .resolve("plugin_data")
            .resolve(info.id)

    override fun getExternalDataDirectory(): File =
        getStarLightDirectory()
            .resolve("plugins")
            .resolve("${info.name}(${info.id})")

    @Deprecated("Deprecated, use getExternalDataDirectory().", ReplaceWith("getExternalDataDirectory()"))
    override fun getDataFolder(): File =
        getExternalDataDirectory()

    override fun getAsset(path: String): File =
        context.getAsset(path)

    override fun toString(): String = info.fullName

    override fun equals(other: Any?): Boolean {
        return when(other) {
            is StarlightPlugin -> other.info.id == this.info.id
            null -> false
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = info.id.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + fileSize.hashCode()
        return result
    }

    final override fun init(
        info        : PluginInfo,
        internalDir : File,
        file        : File,
        classLoader : ClassLoader
    ) {
        this.mInfo         = info
        this.internalDir   = internalDir
        this.originalFile  = file
        this.classLoader   = classLoader
    }

    protected fun getClassLoader(): ClassLoader = classLoader

    protected fun addLanguage(language: Language) {
        var isLoadSuccess = false
        try {
            Session.languageManager.addLanguage(context, language)
            isLoadSuccess = true
        } catch (e: IllegalStateException) {
            logger.error(e)
            e.printStackTrace()
        } finally {
            logger.infoTranslated {
                if (isLoadSuccess) {
                    Locale.ENGLISH { "Successfully added language {name}" }
                    Locale.KOREAN  { "성공적으로 언어 {name}을 추가했어요" }
                } else {
                    Locale.ENGLISH { "Failed to add language {name}" }
                    Locale.KOREAN  { "언어 {name}을 추가하는 데 실패했어요" }
                }
                format(
                    "name" to language.name
                )
            }
        }
    }

    protected fun addWidget(widget: Class<Widget>) =
        Session.widgetManager.addWidget(context, widget)

    protected inline fun <reified T> addWidget() where T : Widget =
        Session.widgetManager.addWidget(context, T::class.java)

    protected fun <T> addApi(api: Api<T>) =
        Session.apiManager.addApi(api)

    protected fun registerProjectEvents(builder: ProjectEventBuilder.() -> Unit) {
        val events = ProjectEventBuilder(context).apply(builder).build()

        for ((id, event) in events) {
            ProjectEventManager.register(id, event)
        }
    }

    protected inline fun <reified T: ProjectEvent> fireProjectEvent(vararg args: Any, noinline onFailure: (project: Project, e: Throwable) -> Unit = { _, _ -> }) =
        Session.projectManager.fireEvent<T>(*args, onFailure = onFailure)
}