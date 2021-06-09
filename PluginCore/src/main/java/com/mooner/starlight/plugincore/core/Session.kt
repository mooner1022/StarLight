package com.mooner.starlight.plugincore.core

import android.os.Environment
import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.ProjectLoader
import java.io.File

class Session {
    companion object {
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

//fun b3EflsEaRR(eventListener: (eventName: String, arguments: Map<String, Any>) -> Unit) {
//    Session.eventBinder.bind(eventListener)
//}

//fun callEvent(eventName: String, arguments: Map<String, Any>) {
//    Session.eventBinder.call(eventName, arguments)
//}