package com.mooner.starlight.api.core

import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.method.MethodType
import com.mooner.starlight.plugincore.models.Message
import com.mooner.starlight.plugincore.project.Project

class BotClient {

    fun on(eventName: String, callback: (event: Message) -> Unit) {

    }
}

class ClientMethod: Method<BotClient>() {

    override val name: String = "BotClient"

    override val type: MethodType = MethodType.CLASS

    override val instanceClass: Class<BotClient> = BotClient::class.java

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
        return BotClient()
    }
}