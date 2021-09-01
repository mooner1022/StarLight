package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.event.EventListener
import com.mooner.starlight.plugincore.config.ConfigObject
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.models.TypedString
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.utils.Utils.Companion.getFileSize
import kotlinx.serialization.decodeFromString
import java.io.File

abstract class StarlightPlugin: Plugin, EventListener {
    private lateinit var projectLoader: ProjectLoader
    private lateinit var loader: PluginLoader
    internal lateinit var file: File
    private lateinit var dataDir: File
    private lateinit var classLoader: ClassLoader
    private var isEnabled = false
    private var configPath: File? = null
    lateinit var config: PluginConfig
    val fileSize: Float
        get() = file.getFileSize()

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
            config: PluginConfig,
            dataDir: File,
            file: File,
    ) {
        val classLoader = this.javaClass.classLoader
        if (classLoader is PluginClassLoader) {
            throw IllegalStateException("Cannot use initialization constructor at runtime")
        }
        init(pluginLoader, projectLoader, config, dataDir, file, classLoader!!)
    }

    override val configObjects: List<ConfigObject> = listOf()

    override val name: String
        get() = config.fullName

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

    fun getPluginConfigs(): Map<String, Any> {
        return if (configPath == null || !configPath!!.isFile || !configPath!!.exists()) mapOf() else {
            val typed: Map<String, TypedString> = Session.json.decodeFromString(configPath!!.readText())
            typed.mapValues { it.value.cast()!! }
        }
    }

    fun callEvent(eventName: String, args: Array<Any>) = Session.projectLoader.callEvent(this.config.id, eventName, args)

    fun getDataFolder(): File = dataDir

    fun getAsset(directory: String): File = File(dataDir, directory)

    fun getProjectLoader(): ProjectLoader = projectLoader

    protected fun getClassLoader(): ClassLoader = classLoader

    fun addLanguage(language: Language) {
        var isLoadSuccess = false
        try {
            Session.getLanguageManager().addLanguage(language)
            isLoadSuccess = true
        } catch (e: IllegalStateException) {
            Logger.e("LanguageLoader", e.toString())
            e.printStackTrace()
        } finally {
            Logger.i("LanguageLoader",(if (isLoadSuccess) "Successfully added" else "Failed to add") + " language ${language.name}")
        }
    }

    override fun toString(): String = config.fullName

    override fun equals(other: Any?): Boolean {
        return when(other) {
            is StarlightPlugin -> other.config.id == this.config.id
            null -> false
            else -> false
        }
    }

    fun init(
        pluginLoader: PluginLoader,
        projectLoader: ProjectLoader,
        config: PluginConfig,
        dataDir: File,
        file: File,
        classLoader: ClassLoader
    ) {
        this.loader = pluginLoader
        this.projectLoader = projectLoader
        this.config = config
        this.dataDir = dataDir
        this.file = file
        this.classLoader = classLoader
    }

    override fun hashCode(): Int {
        var result = dataDir.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + config.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + configObjects.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}