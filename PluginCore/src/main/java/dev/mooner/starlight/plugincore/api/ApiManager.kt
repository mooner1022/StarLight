package dev.mooner.starlight.plugincore.api

import dev.mooner.starlight.plugincore.logger.LoggerFactory
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

    fun getApis(): List<Api<*>> = mApis

    internal fun purge() {
        mApis.clear()
    }
}