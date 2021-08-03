package com.mooner.starlight.plugincore.core

import android.os.Build
import android.os.Environment
import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.ProjectLoader
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
                override fun initialValue(): Json? {
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
        private var mGeneralConfig: GeneralConfig = GeneralConfig(File(Environment.getExternalStorageDirectory(), "StarLight/"))

        private var mLanguageManager: LanguageManager? = null

        private var mProjectLoader: ProjectLoader? = null
        val projectLoader: ProjectLoader
            get() = mProjectLoader!!

        const val isDebugging: Boolean = true

        fun initLanguageManager() {
            if (mLanguageManager != null) {
                Logger.e("init", "Redeclaration of object languageManager")
                return
            }
            mLanguageManager = LanguageManager()
        }
        fun initProjectLoader() {
            if (mProjectLoader != null) {
                Logger.e("init", "Redeclaration of object projectLoader")
                return
            }
            mProjectLoader = ProjectLoader()
        }

        fun getLanguageManager(): LanguageManager = mLanguageManager!!
        fun getGeneralConfig(): GeneralConfig = mGeneralConfig
    }
}