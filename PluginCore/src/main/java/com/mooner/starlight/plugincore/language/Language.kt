package com.mooner.starlight.plugincore.language

abstract class Language: ILanguage {

    private var _config: Map<String, Any> = mapOf()

    internal fun setLanguageConfig(conf: Map<String, Any>) {
        _config = conf
    }

    fun getLanguageConfig(): Map<String, Any> {
        return _config
    }
}