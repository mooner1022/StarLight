package com.mooner.starlight.plugincore.language

import com.mooner.starlight.plugincore.logger.Logger

class LanguageManager {

    private val languages: MutableSet<ILanguage> = HashSet()
    private val languageAssetPaths: MutableMap<String, String> = hashMapOf()

    internal fun getAssetPath(id: String): String? = languageAssetPaths[id]

    fun addLanguage(assetPath: String, lang: ILanguage) {
        if (languages.contains(lang)) {
            throw IllegalArgumentException("Duplicated language: ${lang.name}")
        }
        languages += lang
        languageAssetPaths[lang.id] = assetPath
        Logger.v("LanguageManager", "Added language ${lang.name}(${lang.id})")
    }

    fun getLanguage(id: String, newInstance: Boolean = true): ILanguage? {
        for (lang in languages) {
            if (lang.id == id) {
                return if (newInstance) lang.javaClass.newInstance() else lang
            }
        }
        return null
    }

    fun getLanguages(): Array<ILanguage> {
        return languages.toTypedArray()
    }

    internal fun purge() {
        languages.clear()
        languageAssetPaths.clear()
    }
}