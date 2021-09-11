package com.mooner.starlight.plugincore.method

import com.mooner.starlight.plugincore.logger.Logger

object MethodManager {

    private val mMethods: MutableSet<Method<Any>> = hashSetOf()

    fun <T> addMethod(method: Method<T>) {
        synchronized(mMethods) {
            if (mMethods.find { it.name == method.name } != null) return
            mMethods += method as Method<Any>
            Logger.d("MethodManager", "Added method ${method.name}")
        }
    }

    fun getMethods(): List<Method<Any>> = mMethods.toList()
}