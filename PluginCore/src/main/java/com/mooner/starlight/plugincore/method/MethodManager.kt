package com.mooner.starlight.plugincore.method

import com.mooner.starlight.plugincore.logger.Logger

object MethodManager {

    private val mMethods: MutableSet<Method> = hashSetOf()

    fun addMethod(vararg methods: Method) {
        synchronized(mMethods) {
            for (method in methods) {
                if (method in mMethods) return
                mMethods.add(method)
                Logger.d("MethodManager", "Added method ${method.name}")
            }
        }
    }

    fun getMethods(): List<Method> = mMethods.toList()
}