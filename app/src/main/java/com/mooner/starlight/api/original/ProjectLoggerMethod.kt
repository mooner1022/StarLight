package com.mooner.starlight.api.original

import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction

class ProjectLoggerMethod: Method() {

    override val name: String = "Logger"

    override val instance: Any = LocalLogger
    
    override val functions: List<MethodFunction> = listOf(
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
}