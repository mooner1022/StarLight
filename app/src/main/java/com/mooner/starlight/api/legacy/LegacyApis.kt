package com.mooner.starlight.api.legacy

import com.mooner.starlight.plugincore.method.MethodClass
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.method.MethodManager

class LegacyApis {

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
            MethodFunction(name = "getPhoneModel"),
            MethodFunction(
                name = "randomAlphanumeric",
                args = arrayOf(Int::class.java)
            )
        )
    )

    fun init() {
        MethodManager.addMethod(utilsMethod)
    }
}