package com.mooner.starlight.plugincore.methods

object MethodManager {

    private val methods: HashSet<MethodClass> = hashSetOf()

    fun addMethod(vararg methodClasses: MethodClass) {
        for (block in methodClasses) {
            if (block in methods) return
            methods.add(block)
        }
    }

    fun getMethods(): List<MethodClass> = methods.toList()
}