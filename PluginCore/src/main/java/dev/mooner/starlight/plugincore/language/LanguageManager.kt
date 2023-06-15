package dev.mooner.starlight.plugincore.language

import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate

private val LOG = LoggerFactory.logger {  }

class LanguageManager {

    private val languages: MutableSet<Language> = HashSet()
    private val languageAssetPaths: MutableMap<String, String> = hashMapOf()

    internal fun getAssetPath(id: String): String? =
        languageAssetPaths[id]

    fun addLanguage(assetPath: String, lang: Language) {
        if (languages.contains(lang)) {
            throw IllegalArgumentException("Duplicated language: ${lang.name}")
        }
        languages += lang
        languageAssetPaths[lang.id] = assetPath
        LOG.verbose { 
            translate { 
                Locale.ENGLISH { "Added language ${lang.name}(${lang.id})" }
                Locale.KOREAN  { "${lang.name}(${lang.id}) 언어 추가 성공" }
            }
        }
    }

    fun getLanguage(id: String, newInstance: Boolean = false): Language? {
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

    internal fun purge() {
        languages.clear()
        languageAssetPaths.clear()
    }
}