package com.mooner.starlight.plugincore.language

import com.mooner.starlight.plugincore.Session

class LanguageManager {
    private val languages: HashSet<Language> = HashSet()

    fun addLanguage(lang: Language) {
        if (languages.contains(lang)) {
            Session.getLogger().e("LanguageManager", "Duplicate custom language: ${lang.name}")
            return
        }
        languages.add(lang)
        Session.getLogger().i("LanguageManager", "Successfully added language ${lang.name}")
    }

    fun getLanguage(id: String): Language? {
        for (lang in languages) {
            if (lang.id == id) {
                return lang
            }
        }
        return null
    }

    fun getLanguages(): Array<Language> {
        return languages.toTypedArray()
    }
}