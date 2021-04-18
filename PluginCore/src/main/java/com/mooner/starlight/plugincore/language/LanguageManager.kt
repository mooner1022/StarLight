package com.mooner.starlight.plugincore.language

class LanguageManager {
    private val languages: HashSet<ILanguage> = HashSet()

    fun addLanguage(lang: ILanguage) {
        if (languages.contains(lang)) {
            throw IllegalArgumentException("Duplicate custom language: ${lang.name}")
        }
        languages.add(lang)
    }

    fun getLanguage(id: String): ILanguage? {
        for (lang in languages) {
            if (lang.id == id) {
                return lang
            }
        }
        return null
    }

    fun getLanguages(): Array<ILanguage> {
        return languages.toTypedArray()
    }
}