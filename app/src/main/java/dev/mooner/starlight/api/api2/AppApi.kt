package dev.mooner.starlight.api.api2

import android.content.Context
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Callable

@Suppress("unused")
class AppApi: Api<AppApi.App>() {

    /**
     * MessengerBot R's `App`: the app's Android context, and running code on the main thread; a global in API2
     * projects.
     *
     * Use its static members, as `App.getContext()`. Of MessengerBot R's `App`, StarLight has only `getContext` and
     * `runOnUiThread`: no `isMainThread`, `runDelayed` or `runOnBackgroundThread`.
     */
    class App {

        companion object {

            /**
             * StarLight's application `Context`, for Android APIs that need one.
             *
             * @see guide:engines/neonjs#java-access
             */
            @JvmStatic
            fun getContext(): Context = GlobalApplication.requireContext()

            /* Incomplete implementation */
            /**
             * Runs [task] on Android's main (UI) thread, then calls [onComplete] there with its error or result.
             *
             * It returns at once; the task runs later. Unlike MessengerBot R, [onComplete] isn't optional: pass a
             * function that does nothing when the result isn't needed. An error thrown in [onComplete] itself isn't
             * caught and stops the app, so catch errors inside it. Long work in [task] freezes StarLight's screens.
             *
             * @param task The function to run, with no arguments.
             * @param onComplete Called with `(error, result)`: the Java error [task] threw and `null`, or `null` and
             * what [task] returned.
             * @return A Kotlin coroutine `Job` for the scheduled task; scripts can ignore it.
             * @see guide:engines/neonjs#java-access
             */
            @JvmStatic
            fun runOnUiThread(task: Callable<Any>, onComplete: (error: Throwable?, result: Any?) -> Unit) =
                CoroutineScope(Dispatchers.Main).launch {
                    runCatching {
                        task.call()
                    }.onFailure {
                        onComplete(it, null)
                    }.onSuccess {
                        onComplete(null, it)
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