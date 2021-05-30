package com.mooner.starlight.plugincore

import android.os.Environment
import com.mooner.starlight.plugincore.core.GeneralConfig
import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.theme.ThemeManager
import java.io.File

class Session {
    companion object {
        @Suppress("DEPRECATION")
        private var lGeneralConfig: GeneralConfig = GeneralConfig(File(Environment.getExternalStorageDirectory(), "StarLight/"))
        private var lLanguageManager: LanguageManager? = null
        private var lProjectLoader: ProjectLoader? = null

        const val isDebugging: Boolean = true

        fun initLanguageManager() {
            if (lLanguageManager != null) {
                Logger.e("init", "Redeclaration of object languageManager")
                return
            }
            lLanguageManager = LanguageManager()
        }
        fun initProjectLoader() {
            if (lProjectLoader != null) {
                Logger.e("init", "Redeclaration of object projectLoader")
                return
            }
            lProjectLoader = ProjectLoader()
        }

        fun getProjectLoader(): ProjectLoader = lProjectLoader!!
        fun getLanguageManager(): LanguageManager = lLanguageManager!!
        fun getGeneralConfig(): GeneralConfig = lGeneralConfig
    }
}

//fun b3EflsEaRR(eventListener: (eventName: String, arguments: Map<String, Any>) -> Unit) {
//    Session.eventBinder.bind(eventListener)
//}

//fun callEvent(eventName: String, arguments: Map<String, Any>) {
//    Session.eventBinder.call(eventName, arguments)
//}