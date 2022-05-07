package dev.mooner.starlight.plugincore.api

import dev.mooner.starlight.plugincore.logger.Logger

class ApiManager {

    private val mApis: MutableList<Api<*>> = arrayListOf()

    fun <T> addApi(api: Api<T>) {
        synchronized(mApis) {
            if (mApis.find { it.name == api.name } != null) return
            mApis += api
            Logger.v("ApiManager", "Added api ${api.name}")
        }
    }

    fun getApis(): List<Api<*>> = mApis

    internal fun purge() {
        mApis.clear()
    }
}