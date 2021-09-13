package com.mooner.starlight.plugincore.language

class LanguageManager {
    private val languages: HashSet<Language> = HashSet()

    fun addLanguage(lang: Language) {
        if (languages.contains(lang)) {
            throw IllegalArgumentException("Duplicated language: ${lang.name}")
        }
        languages.add(lang)
    }

    fun getLanguage(id: String, newInstance: Boolean = true): Language? {
        for (lang in languages) {
            if (lang.id == id) {
                return if (newInstance) lang.javaClass.newInstance() else lang
            }
        }
        return null
    }

    fun getLanguages(): Array<Language> {
        return languages.toTypedArray()
    }
}