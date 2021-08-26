package com.mooner.starlight.plugincore.methods.original

import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.methods.MethodClass
import com.mooner.starlight.plugincore.methods.MethodFunction
import com.mooner.starlight.plugincore.methods.MethodManager

class OriginalMethods {

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

    private val projectsMethod = MethodClass(
        className = "Projects",
        clazz = Projects::class.java,
        instance = Projects,
        functions = listOf(
            MethodFunction(
                name = "get",
                args = arrayOf(String::class.java)
            )
        )
    )

    private val languagesMethod = MethodClass(
        className = "Languages",
        clazz = Languages::class.java,
        instance = Languages,
        functions = listOf(
            MethodFunction(
                name = "get",
                args = arrayOf(String::class.java)
            )
        )
    )

    fun init() {
        MethodManager.addMethod(projectsMethod, languagesMethod, loggerMethod)
    }
}