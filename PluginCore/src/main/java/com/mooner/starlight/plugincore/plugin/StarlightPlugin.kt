package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.language.Language
import java.io.File
import java.util.logging.Logger

abstract class StarlightPlugin: Plugin {
    private lateinit var manager: PluginManager
    private lateinit var loader: PluginLoader
    private lateinit var file: File
    private lateinit var dataDir: File
    private lateinit var config: PluginConfig
    private lateinit var classLoader: ClassLoader
    private lateinit var logger: Logger
    private var isEnabled = false

    constructor() {
        val classLoader = this.javaClass.classLoader
        if (classLoader !is PluginClassLoader) {
            throw IllegalStateException("StarlightPlugin requires ${PluginClassLoader::class.java.name}")
        }
        classLoader.initialize(this)
    }

    protected constructor(
        loader: PluginLoader,
        config: PluginConfig,
        dataDir: File,
        file: File,
    ) {
        val classLoader = this.javaClass.classLoader
        if (classLoader is PluginClassLoader) {
            throw IllegalStateException("Cannot use initialization constructor at runtime")
        }
        init(loader, config, dataDir, file, classLoader!!)
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

    fun getLogger(): Logger = logger

    fun getDataFolder(): File = dataDir

    protected fun getClassLoader(): ClassLoader = classLoader

    override val pluginManager: PluginManager
        get() = manager

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
        loader: PluginLoader,
        config: PluginConfig,
        dataDir: File,
        file: File,
        classLoader: ClassLoader
    ) {
        this.loader = loader
        this.config = config
        this.dataDir = dataDir
        this.file = file
        this.classLoader = classLoader
    }
}