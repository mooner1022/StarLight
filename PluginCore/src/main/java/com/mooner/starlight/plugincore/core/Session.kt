package com.mooner.starlight.plugincore.core

import android.os.Build
import android.os.Environment
import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.project.ProjectManager
import com.mooner.starlight.plugincore.widget.WidgetManager
import kotlinx.serialization.json.Json
import java.io.File

class Session {

    companion object {

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
        val generalConfig: GeneralConfig =
            GeneralConfig(File(Environment.getExternalStorageDirectory(), "StarLight/"))

        private var mLanguageManager: LanguageManager? = null
        val languageManager: LanguageManager
            get() = mLanguageManager!!

        private var mProjectLoader: ProjectLoader? = null
        private var mProjectManager: ProjectManager? = null
        val projectManager: ProjectManager
            get() = mProjectManager!!
        val projectLoader: ProjectLoader
            get() = mProjectLoader!!

        private var mPluginLoader: PluginLoader? = null
        val pluginLoader: PluginLoader
            get() = mPluginLoader!!

        private var mWidgetManager: WidgetManager? = null
        val widgetManager: WidgetManager
            get() = mWidgetManager!!

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
            mProjectManager  = ProjectManager(projectDir)
            mProjectLoader   = ProjectLoader(projectDir)
        }
    }
}