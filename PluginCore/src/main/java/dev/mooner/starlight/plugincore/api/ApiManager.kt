package dev.mooner.starlight.plugincore.api

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.TranslationManager

private val LOG = LoggerFactory.logger {  }

class ApiManager {

    private val mApis: MutableList<Api<*>> = arrayListOf()

    fun <T> addApi(api: Api<T>) {
        synchronized(mApis) {
            if (mApis.find { it.name == api.name } != null) {
                LOG.warn {
                    TranslationManager.translate {
                        Locale.ENGLISH { "Found duplicate Api with name ${api.name}, please check plugin compatibility." }
                        Locale.KOREAN  { "중복된 이름의 Api ${api.name} 발견, 플러그인 호환성을 다시 확인해주세요." }
                    }
                }
                return
            }
            mApis += api
            LOG.debug { 
                TranslationManager.translate { 
                    Locale.ENGLISH { "Added api ${api.name}" }
                    Locale.KOREAN  { "${api.name} Api 등록 성공" }
                }
            }
        }
    }

    /** Every API, whichever levels it belongs to. */
    fun getApis(): List<Api<*>> =
        synchronized(mApis) { mApis.toList() }

    /**
     * The APIs a project that chose [levelIds] gets, without the globals [language] provides itself. A `null` [levelIds]
     * is a project from before levels, which gets every API.
     */
    fun getApis(levelIds: Set<String>?, language: Language? = null): List<Api<*>> {
        val native = language?.nativeGlobals.orEmpty()
        return getApis().filter { api ->
            api.name !in native && Session.apiLevelManager.hasApi(levelIds, api.name)
        }
    }

    /** The APIs [project] gets, from its levels and its language. */
    fun getApis(project: Project): List<Api<*>> =
        getApis(project.info.apiLevels, runCatching { project.getLanguage() }.getOrNull())

    internal fun purge() {
        mApis.clear()
    }
}