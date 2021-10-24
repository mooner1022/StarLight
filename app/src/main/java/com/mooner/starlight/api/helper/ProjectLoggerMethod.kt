package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.method.MethodType
import com.mooner.starlight.plugincore.project.Project

class ProjectLoggerMethod: Method<LocalLogger>() {

    override val name: String = "Logger"

    override val type: MethodType = MethodType.OBJECT

    override val instanceClass: Class<LocalLogger> = LocalLogger::class.java
    
    override val functions: List<MethodFunction> = listOf(
        function {
            name = "i"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "e"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "w"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "d"
            args = arrayOf(String::class.java, String::class.java)
        }
    )

    override fun getInstance(project: Project): Any = project.logger
}