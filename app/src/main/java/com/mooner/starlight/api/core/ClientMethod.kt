package com.mooner.starlight.api.core

import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.models.Message
import com.mooner.starlight.plugincore.project.Project

class ClientMethod: Method() {

    class Client {

        fun on(eventName: String, callback: (event: Message) -> Unit) {

        }
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
        return Client::class.java
    }
}