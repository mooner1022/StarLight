package com.mooner.starlight.plugincore.language

abstract class Language: ILanguage {

    private val _config: Map<String, Any> = mapOf()

    fun getLanguageConfig(): Map<String, Any> {
        return _config
    }
}