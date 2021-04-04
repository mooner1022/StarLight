package com.mooner.starlight.plugincore

import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.logger.Logger

class Session {
    companion object {
        private val logger: Logger = Logger()
        private val languageManager: LanguageManager = LanguageManager()

        fun getLogger(): Logger = logger
        fun getLanguageManager(): LanguageManager = languageManager
    }
}

//fun b3EflsEaRR(eventListener: (eventName: String, arguments: Map<String, Any>) -> Unit) {
//    Session.eventBinder.bind(eventListener)
//}

//fun callEvent(eventName: String, arguments: Map<String, Any>) {
//    Session.eventBinder.call(eventName, arguments)
//}