package com.mooner.starlight.plugincore.methods.original

import com.mooner.starlight.plugincore.methods.MethodClass
import com.mooner.starlight.plugincore.methods.MethodFunction
import com.mooner.starlight.plugincore.methods.MethodManager

class OriginalMethods {

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
        MethodManager.addMethod(projectsMethod, languagesMethod)
    }
}