package com.mooner.starlight.api.api2

import android.content.Context
import com.mooner.starlight.core.GlobalApplication
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiObject
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable

class AppApi: Api<AppApi.App>() {

    class App {

        companion object {

            @JvmStatic
            fun getContext(): Context = GlobalApplication.requireContext()

            /* Incomplete implementation */
            @JvmStatic
            fun runOnUiThread(task: Callable<Any>, onComplete: (error: Throwable?, result: Any?) -> Unit) {
                runBlocking {
                    val flow = flow<Any> {
                        try {
                            emit(task.call())
                        } catch (e: Throwable) {
                            emit(e)
                        }
                    }.flowOn(Dispatchers.Main)
                    flow.collect { result ->
                        if (result is Throwable) {
                            onComplete(result, null)
                        } else {
                            onComplete(null, result)
                        }
                    }
                }
            }
        }
    }

    override val name: String = "App"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "getContext"
            returns = Context::class.java
        },
        function {
            name = "runOnUiThread"
            args = arrayOf(Callable::class.java, Function2::class.java)
        }
    )

    override val instanceClass: Class<App> = App::class.java

    override val instanceType: InstanceType = InstanceType.CLASS

    override fun getInstance(project: Project): Any = App::class.java
}