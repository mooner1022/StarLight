package com.mooner.starlight.api.unused

/*
 * TODO: 콜백 함수 구현 문제 해결. 각 언어별 Implementation?
 */

/*
class ClientApi: Api<ClientApi.BotClient>() {

    class BotClient {

        fun on(eventName: String, callback: Callback) {
        }
    }

    interface Callback {
        fun call(event: String)
    }

    override val name: String = "BotClient"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<BotClient> = BotClient::class.java

    override val objects: List<ApiFunction> = listOf(
        function {
            name = "on"
            args = arrayOf(String::class.java, Callback::class.java)
        },
        //function {
        //    name = "once"
        //    args = arrayOf(String::class.java, Function::class.java)
        //}
    )

    override fun getInstance(project: Project): Any {
        return BotClient()
    }
}
 */