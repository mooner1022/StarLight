package com.mooner.starlight.plugincore.method

import com.mooner.starlight.plugincore.logger.Logger

object MethodManager {

    private val methods: HashSet<MethodClass> = hashSetOf()

    fun addMethod(vararg methodClasses: MethodClass) {
        synchronized(methods) {
            for (block in methodClasses) {
                if (block in methods) return
                methods.add(block)
                Logger.d("MethodManager", "Added method ${block.className}")
            }
        }
    }

    fun getMethods(): List<MethodClass> = methods.toList()
}