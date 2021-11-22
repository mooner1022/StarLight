package com.mooner.starlight.api.core

import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.models.Message
import com.mooner.starlight.plugincore.project.Project

class BotClient {

    fun on(eventName: String, callback: (event: Message) -> Unit) {

    }
}

class ClientApi: Api<BotClient>() {

    override val name: String = "BotClient"

    override val instanceType: InstanceType = InstanceType.CLASS

    override val instanceClass: Class<BotClient> = BotClient::class.java

    override val objects: List<ApiFunction> = listOf(
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