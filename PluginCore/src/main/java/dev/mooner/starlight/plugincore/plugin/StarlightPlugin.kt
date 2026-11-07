/*
 * StarlightPlugin.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import androidx.annotation.CallSuper
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.config.data.ConfigData
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
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
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetDefinition
import java.io.File

private val logger = LoggerFactory.logger {  }

/**
 * A loaded plugin, as `Plugins.ofId(id)`, `Plugins.ofName(name)` and `Plugins.getAll()` return it.
 *
 * Scripts mostly check that one is loaded and read its `info`. The APIs it adds are globals of their own, not members
 * of this. Its other public members are StarLight's own, and aren't listed here.
 */
@Suppress("unused")
abstract class StarlightPlugin: Plugin, EventListener {

    // A public field to Java, as a lateinit's is: hidden from scripts, which would otherwise see and set it.
    @field:JvmSynthetic
    internal lateinit var originalFile: File
    private lateinit var internalDir: File

    private var isEnabled = false
    private val listeners: MutableSet<EventListener> = hashSetOf()

    private lateinit var classLoader: ClassLoader
    private lateinit var mInfo: PluginInfo
    /**
     * The plugin's ID, name, version and authors, from its package.
     */
    override val info: PluginInfo
        get() = mInfo

    private var configCache: MutableConfig? = null

    /** @suppress */
    val context: PluginContext by lazy {
        PluginContextImpl(info.id, info.name, getInternalDataDirectory().path)
    }

    /** @suppress */
    val fileSize: Float
        get() = originalFile.getFileSize()
    /** @suppress */
    val fileName: String
        get() = originalFile.name

    constructor() {
        val classLoader = this.javaClass.classLoader
        if (classLoader !is PluginHostClassLoader) {
            throw IllegalStateException("StarlightPlugin must be loaded by the plugin loader")
        }
        classLoader.initialize(this)
    }

    protected constructor(
        config: PluginInfo,
        internalDir: File,
        file: File,
    ) {
        val classLoader = this.javaClass.classLoader
        if (classLoader is PluginHostClassLoader) {
            throw IllegalStateException("Cannot use initialization constructor at runtime")
        }
        init(config, file, internalDir, classLoader!!)
    }

    /** @suppress */
    override fun getConfigStructure(): ConfigStructure { return emptyList() }

    /** @suppress */
    @CallSuper
    override fun onConfigUpdated(config: ConfigData, updated: Map<String, Set<String>>) =
        invalidateConfigCache()

    /** @suppress */
    override fun onConfigValueUpdated(id: String, data: Any) {}

    /**
     * Whether this plugin still needs the values asked for by [getSetupStructure].
     *
     * The app asks this on the main thread whenever it shows the plugin, including every time the
     * plugin list is drawn, so it must be cheap: read a field, don't do I/O. Return `true` only when
     * [getSetupStructure] has something to show. When this turns `true` while the plugin runs, such as
     * when a saved token is rejected, call [requestSetup] so the app notices without waiting for a redraw.
     * @suppress
     */
    override fun isSetupRequired(): Boolean = false

    /**
     * Why the setup is needed, shown at the top of the setup dialog in place of the generic message,
     * e.g. that the saved token was rejected. `null` for the first setup. Same rules as [isSetupRequired].
     * @suppress
     */
    override fun getSetupReason(): String? = null

    /**
     * Tells the app that this plugin needs its setup again. Make [isSetupRequired] return `true` first.
     *
     * The app updates the plugin list and, while it is open, shows the setup dialog unless the user put
     * off the setup for the same [getSetupReason]. Safe to call from any thread.
     */
    protected fun requestSetup() {
        EventHandler.fireEventWithScope(Events.Plugin.SetupRequested(this))
    }

    /**
     * Items shown in the setup dialog, which the app opens on launch while [isSetupRequired] is `true`
     * (unless the user put it off for the same [getSetupReason]) and from the plugin list or info
     * screen at any time.
     *
     * This is separate from [getConfigStructure], and the app doesn't save what is entered here:
     * the values only reach [onSetup].
     * @suppress
     */
    override fun getSetupStructure(): ConfigStructure = emptyList()

    /**
     * Receives the values entered in the setup dialog when the user confirms it.
     *
     * Called on the main thread, so switch dispatchers for I/O or network checks. Items the user
     * didn't touch are absent from [values], as in the plugin config, so read them with a default.
     * The app doesn't save [values]: keep what you need yourself, or call [saveToConfig].
     *
     * @return `null` when setup is done, or a message to show while the dialog stays open.
     * @suppress
     */
    override suspend fun onSetup(values: ConfigData): String? = null

    /**
     * Whether the plugin is enabled, as every plugin is once it has loaded; scripts read it as `enabled`.
     */
    override fun isEnabled(): Boolean = isEnabled

    /**
     * Calls `onEnable` on the plugin's listeners, itself included, and then marks it enabled.
     *
     * The flag is set last, so a listener added during `onEnable` isn't also enabled by [addListener], and a plugin
     * whose `onEnable` throws doesn't report itself enabled.
     */
    internal fun enableOnLoad() {
        getListeners().forEach(EventListener::onEnable)
        isEnabled = true
    }

    /**
     * Marks the plugin disabled as StarLight unloads it, and calls `onDisable` on every listener, as loading calls
     * `onEnable` on them. A listener that throws is logged, and the rest are still disabled.
     */
    internal fun disableOnUnload() {
        isEnabled = false
        for (listener in getListeners()) {
            try {
                listener.onDisable()
            } catch (e: Throwable) {
                logger.error(e) { "Failed to disable a listener of plugin ${info.id}" }
            }
        }
    }

    protected fun addListener(listener: EventListener) {
        listeners += listener
        if (isEnabled)
            listener.onEnable()
    }

    /**
     * Returns all event listeners associated with this plugin, including the plugin itself.
     *
     * @return Set of event listeners
     * @suppress
     */
    fun getListeners(): Set<EventListener> =
        listeners + this

    /**
     * Invalidate cached config data inside plugin instance
     * @suppress
     */
    fun invalidateConfigCache() {
        configCache = null
    }

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

    protected fun getPluginConfig(): ConfigData =
        getMutablePluginConfig()

    /**
     * Writes [values], such as those passed to [onSetup], into the plugin config shown by
     * [getConfigStructure]. Entries already there are replaced and the rest are kept.
     */
    protected fun saveToConfig(values: ConfigData) {
        getMutablePluginConfig().edit {
            for ((categoryId, entries) in values.getData())
                for ((key, value) in entries)
                    category(categoryId).setRaw(key, value)
        }
    }

    private fun getMutablePluginConfig(): MutableConfig {
        return configCache ?: let {
            val configPath = getExternalDataDirectory().resolve("config-plugin.json")
            FileConfig(configPath).also {
                configCache = it
            }
        }
    }

    /** @suppress */
    override fun getInternalDataDirectory(): File =
        internalDir
            .resolve("plugin_data")
            .resolve(info.id)

    /** @suppress */
    override fun getExternalDataDirectory(): File =
        getStarLightDirectory()
            .resolve("plugins")
            .resolve("${info.name}(${info.id})")

    /** @suppress */
    @Deprecated("Deprecated, use getExternalDataDirectory().", ReplaceWith("getExternalDataDirectory()"))
    override fun getDataFolder(): File =
        getExternalDataDirectory()

    /** @suppress */
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

    override fun hashCode(): Int =
        info.id.hashCode()

    /** @suppress */
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

    protected fun addWidget(widget: Class<out Widget>) =
        Session.widgetManager.addWidget(context, widget)

    protected fun addWidget(definition: WidgetDefinition) =
        Session.widgetManager.addWidget(context, definition)

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