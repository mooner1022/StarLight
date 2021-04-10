package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.ProjectLoader
import java.io.File

abstract class StarlightPlugin: Plugin {
    private lateinit var projectLoader: ProjectLoader
    private lateinit var loader: PluginLoader
    private lateinit var file: File
    private lateinit var dataDir: File
    private lateinit var config: PluginConfig
    private lateinit var classLoader: ClassLoader
    private var isEnabled = false

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

    fun getLogger(): Logger = Session.getLogger()

    fun getDataFolder(): File = dataDir

    fun getProjectLoader(): ProjectLoader = projectLoader

    protected fun getClassLoader(): ClassLoader = classLoader

    fun addCustomLanguage(language: Language) {
        try {
            Session.getLanguageManager().addLanguage(language)
            Session.getLogger().i("PluginLoader","Successfully added language ${language.name}")
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            Session.getLogger().e("PluginLoader",e.toString())
        }
    }

    override fun toString(): String = config.fullName

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
}