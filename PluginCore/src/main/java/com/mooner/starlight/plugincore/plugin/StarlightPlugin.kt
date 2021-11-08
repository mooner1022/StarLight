package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.Config
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.language.ILanguage
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.models.TypedString
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.utils.Utils.Companion.getFileSize
import com.mooner.starlight.plugincore.widget.IWidget
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

    fun getPluginConfigs(): Config {
        return if (configPath == null || !configPath!!.isFile || !configPath!!.exists()) Config(emptyMap()) else {
            val loadedMap: Map<String, Map<String, TypedString>> = Session.json.decodeFromString(configPath!!.readText())
            Config(loadedMap)
        }
    }

    fun callEvent(eventName: String, args: Array<Any>) = Session.projectManager.callEvent(this.info.id, eventName, args)

    fun getDataFolder(): File = dataDir

    fun getAsset(directory: String): File = File(dataDir, directory)

    fun getProjectLoader(): ProjectLoader = projectLoader

    protected fun getClassLoader(): ClassLoader = classLoader

    @Deprecated(
        message = "Retained for legacy compatability, don't use it.",
        replaceWith = ReplaceWith("addLanguage(language: ILanguage)", "com.mooner.starlight.plugincore.plugin.StarlightPlugin.addLanguage")
    )
    fun addLanguage(language: Language) = addLanguage(language as ILanguage)

    fun addLanguage(language: ILanguage) {
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

    fun addWidget(widget: IWidget) = Session.widgetManager.addWidget(widget)

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