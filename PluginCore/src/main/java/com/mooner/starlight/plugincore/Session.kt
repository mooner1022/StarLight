package com.mooner.starlight.plugincore

import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.ProjectLoader

class Session {
    companion object {
        private val logger: Logger = Logger()

        private var l_languageManager: LanguageManager? = null
        private var l_projectLoader: ProjectLoader? = null

        const val isDebugging: Boolean = true

        fun initLanguageManager() {
            if (l_languageManager != null) {
                getLogger().e("init", "Redeclaration of object languageManager")
                return
            }
            l_languageManager = LanguageManager()
        }
        fun initProjectLoader() {
            if (l_projectLoader != null) {
                getLogger().e("init", "Redeclaration of object projectLoader")
                return
            }
            l_projectLoader = ProjectLoader()
        }

        fun getLogger(): Logger = logger
        fun getProjectLoader(): ProjectLoader = l_projectLoader!!
        fun getLanguageManager(): LanguageManager = l_languageManager!!
    }
}

//fun b3EflsEaRR(eventListener: (eventName: String, arguments: Map<String, Any>) -> Unit) {
//    Session.eventBinder.bind(eventListener)
//}

//fun callEvent(eventName: String, arguments: Map<String, Any>) {
//    Session.eventBinder.call(eventName, arguments)
//}