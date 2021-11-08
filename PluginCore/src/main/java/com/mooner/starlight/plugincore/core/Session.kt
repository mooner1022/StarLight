package com.mooner.starlight.plugincore.core

import android.os.Build
import android.os.Environment
import com.mooner.starlight.plugincore.api.ApiManager
import com.mooner.starlight.plugincore.config.GlobalConfig
import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.plugincore.plugin.PluginManager
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.project.ProjectManager
import com.mooner.starlight.plugincore.utils.NetworkUtil
import com.mooner.starlight.plugincore.widget.WidgetManager
import kotlinx.serialization.json.Json
import java.io.File

object Session {

    private val mJson: ThreadLocal<Json> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ThreadLocal.withInitial {
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                prettyPrint = true
            }
        }
    } else {
        object : ThreadLocal<Json>() {
            override fun initialValue(): Json {
                return Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                    prettyPrint = true
                }
            }
        }
    }
    val json: Json
        get() = mJson.get()!!

    @Suppress("DEPRECATION")
    val globalConfig: GlobalConfig =
        GlobalConfig(File(Environment.getExternalStorageDirectory(), "StarLight/"))

    private var mLanguageManager: LanguageManager? = null
    val languageManager get() = mLanguageManager!!

    private var mProjectLoader: ProjectLoader? = null
    private var mProjectManager: ProjectManager? = null
    val projectManager get() = mProjectManager!!
    val projectLoader get() = mProjectLoader!!

    private var mPluginLoader: PluginLoader? = null
    private var mPluginManager: PluginManager? = null
    val pluginLoader get() = mPluginLoader!!
    val pluginManager get() = mPluginManager!!

    private var mWidgetManager: WidgetManager? = null
    val widgetManager get() = mWidgetManager!!

    const val isDebugging: Boolean = true

    fun init(baseDir: File) {
        val preStack = Thread.currentThread().stackTrace[2]
        if (!preStack.className.startsWith("com.mooner.starlight")) {
            throw IllegalAccessException("Illegal access to internal function init()")
        }

        val projectDir = File(baseDir, "projects/")

        mLanguageManager = LanguageManager()
        mWidgetManager   = WidgetManager()
        mPluginLoader    = PluginLoader()
        mPluginManager   = PluginManager()
        mProjectManager  = ProjectManager(projectDir)
        mProjectLoader   = ProjectLoader(projectDir)
    }

    fun shutdown() {
        val preStack = Thread.currentThread().stackTrace[2]
        if (!preStack.className.startsWith("com.mooner.starlight")) {
            throw IllegalAccessException("Illegal access to internal function shutdown()")
        }

        globalConfig.push()

        NetworkUtil.purge()
        ApiManager.purge()

        mLanguageManager?.purge()
        mWidgetManager?.purge()
        mPluginLoader?.purge()
        mPluginManager?.purge()
        mProjectManager?.purge()
        mLanguageManager = null
        mWidgetManager = null
        mPluginManager = null
        mPluginLoader = null
        mProjectManager = null

        mJson.remove()
    }
}