package com.mooner.starlight.plugincore.language

import java.nio.file.Path
import kotlin.io.path.Path

class LanguageManager {
    private val languages: MutableSet<ILanguage> = HashSet()

    fun addLanguage(assetPath: String, lang: ILanguage) {
        val path = Path(assetPath)
        addLanguage(path, lang)
    }

    fun addLanguage(assetPath: Path, lang: ILanguage) {
        if (languages.contains(lang)) {
            throw IllegalArgumentException("Duplicated language: ${lang.name}")
        }
        languages.add(lang)
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
}