package com.mooner.starlight.plugincore.methods.legacy

import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.methods.MethodClass
import com.mooner.starlight.plugincore.methods.MethodFunction
import com.mooner.starlight.plugincore.methods.MethodManager
import com.mooner.starlight.plugincore.methods.original.Languages
import com.mooner.starlight.plugincore.methods.original.Projects

class LegacyMethods {

    private val loggerMethod = MethodClass(
        className = "Logger",
        clazz = LocalLogger::class.java,
        instance = Logger,
        functions = listOf(
            MethodFunction(
                name = "i",
                args = arrayOf(String::class.java, String::class.java)
            ),
            MethodFunction(
                name = "e",
                args = arrayOf(String::class.java, String::class.java)
            ),
            MethodFunction(
                name = "w",
                args = arrayOf(String::class.java, String::class.java)
            ),
            MethodFunction(
                name = "d",
                args = arrayOf(String::class.java, String::class.java)
            ),
        )
    )

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

    init {
        MethodManager.addMethod(loggerMethod, utilsMethod)
    }
}