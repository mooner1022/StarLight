package dev.mooner.starlight.api.api2

import android.content.Context
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable

@Suppress("unused")
class AppApi: Api<AppApi.App>() {

    class App {

        companion object {

            @JvmStatic
            fun getContext(): Context = GlobalApplication.requireContext()

            /* Incomplete implementation */
            @JvmStatic
            fun runOnUiThread(task: Callable<Any>, onComplete: (error: Throwable?, result: Any?) -> Unit) = runBlocking {
                flow<Any> {
                    try {
                        emit(task.call())
                    } catch (e: Throwable) {
                        emit(e)
                    }
                }.flowOn(Dispatchers.Main)
                    .collect { result ->
                        if (result is Throwable) {
                            onComplete(result, null)
                        } else {
                            onComplete(null, result)
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