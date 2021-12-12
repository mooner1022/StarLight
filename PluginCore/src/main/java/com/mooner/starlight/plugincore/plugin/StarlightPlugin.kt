package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiManager
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.Config
import com.mooner.starlight.plugincore.config.ConfigImpl
import com.mooner.starlight.plugincore.config.TypedString
import com.mooner.starlight.plugincore.event.Event
import com.mooner.starlight.plugincore.event.callEvent
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.utils.Utils.Companion.getFileSize
import com.mooner.starlight.plugincore.widget.Widget
import kotlinx.serialization.decodeFromString
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.pathString

abstract class StarlightPlugin: Plugin, EventListener {
    private lateinit var projectLoader: ProjectLoader
    private lateinit var loader: PluginLoader
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
        pluginLoader: PluginLoader,
        projectLoader: ProjectLoader,
        config: PluginInfo,
        dataDir: File,
        file: File,
    ) {
        val classLoader = this.javaClass.classLoader
        if (classLoader is PluginClassLoader) {
            throw IllegalStateException("Cannot use initialization constructor at runtime")
        }
        init(pluginLoader, projectLoader, config, dataDir, file, classLoader!!)
    }

    override val configObjects: List<CategoryConfigObject> = listOf()

    override val name: String
        get() = info.fullName

    override fun isEnabled(): Boolean = isEnabled

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

    fun getPluginConfig(): Config {
        return if (configPath == null || !configPath!!.isFile || !configPath!!.exists()) ConfigImpl(emptyMap()) else {
            val loadedMap: Map<String, Map<String, TypedString>> = Session.json.decodeFromString(configPath!!.readText())
            ConfigImpl(loadedMap)
        }
    }

    fun getDataFolder(): File = dataDir

    fun getAsset(directory: String): File = File(dataDir.resolve("assets"), directory)

    fun getProjectLoader(): ProjectLoader = projectLoader

    protected fun getClassLoader(): ClassLoader = classLoader

    fun addLanguage(language: Language) {
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

    fun addWidget(widget: Widget) = Session.widgetManager.addWidget(widget)

    fun <T> addApi(api: Api<T>) = ApiManager.addApi(api)

    protected inline fun <reified T: Event> callEvent(args: Array<out Any>, noinline onError: (e: Throwable) -> Unit = {}): Boolean = Session.eventManager.callEvent<T>(args, onError)

    override fun toString(): String = info.fullName

    override fun equals(other: Any?): Boolean {
        return when(other) {
            is StarlightPlugin -> other.info.id == this.info.id
            null -> false
            else -> false
        }
    }

    fun init(
        pluginLoader: PluginLoader,
        projectLoader: ProjectLoader,
        info: PluginInfo,
        dataDir: File,
        file: File,
        classLoader: ClassLoader
    ) {
        this.loader = pluginLoader
        this.projectLoader = projectLoader
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
        result = 31 * result + name.hashCode()
        return result
    }
}