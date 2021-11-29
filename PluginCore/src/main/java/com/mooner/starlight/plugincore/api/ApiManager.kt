package com.mooner.starlight.plugincore.api

import com.mooner.starlight.plugincore.logger.Logger

object ApiManager {

    private val mApis: MutableList<IApi<Any>> = arrayListOf()

    @Suppress("UNCHECKED_CAST")
    fun <T> addApi(api: IApi<T>) {
        synchronized(mApis) {
            if (mApis.find { it.name == api.name } != null) return
            mApis += api as Api<Any>
            Logger.v("MethodManager", "Added method ${api.name}")
        }
    }

    fun getApis(): List<IApi<Any>> = mApis

    internal fun purge() {
        mApis.clear()
    }
}