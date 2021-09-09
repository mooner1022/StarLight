package com.mooner.starlight.api.core

import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.project.Project

class ClientMethod: Method() {

    class Client {

    }

    override val name: String = "Client"

    override val functions: List<MethodFunction> = listOf(
        function {
            name = "on"
            args = arrayOf(String::class.java, Function::class.java)
        },
        function {
            name = "once"
            args = arrayOf(String::class.java, Function::class.java)
        }
    )

    override fun getInstance(project: Project): Any {
        return Unit
    }
}