package com.mooner.starlight.plugincore.methods.legacy

import com.mooner.starlight.plugincore.methods.MethodClass
import com.mooner.starlight.plugincore.methods.MethodFunction
import com.mooner.starlight.plugincore.methods.MethodManager

class LegacyMethods {

    private val utilsMethod = MethodClass(
        className = "Utils",
        clazz = Utils::class.java,
        instance = Utils,
        functions = listOf(
            MethodFunction(
                name = "getWebText",
                args = arrayOf(String::class.java)
            ),
            MethodFunction(
                name = "parse",
                args = arrayOf(String::class.java)
            ),
            MethodFunction(name = "getAndroidVersionCode"),
            MethodFunction(name = "getAndroidVersionName"),
            MethodFunction(name = "getPhoneBrand"),
            MethodFunction(name = "getPhoneModel")
        )
    )

    fun init() {
        MethodManager.addMethod(utilsMethod)
    }
}