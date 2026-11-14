package dev.mooner.starlight.api.api2

import dev.mooner.starlight.api.msgbot.ScriptVersion
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.script.ScriptTimers
import dev.mooner.starlight.script.runCallback
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ScriptableObject
import kotlin.coroutines.cancellation.CancellationException

private typealias AppTask = () -> Any?
private typealias AppComplete = (error: Throwable?, result: Any?) -> Unit

private fun requireTask(task: AppTask?): AppTask =
    task ?: throw IllegalArgumentException("function must be a function")

/**
 * The callback a script passed as `onComplete`: `null` and `undefined` (which Rhino passes as the text "undefined")
 * as none, a function of either engine as a call of it with `(error, result)`; what it returns is dropped.
 *
 * @throws IllegalArgumentException `"onComplete must be a function or null"` for anything else.
 */
@Suppress("UNCHECKED_CAST")
private fun completeOf(value: Any?): AppComplete? = when {
    ScriptArgs.isMissing(value) -> null
    value is NeonValue && value.isFunction -> neonComplete(value)
    value is org.mozilla.javascript.Function -> rhinoComplete(value)
    value is Function2<*, *, *> -> value as AppComplete
    else -> throw IllegalArgumentException("onComplete must be a function or null")
}

private fun neonComplete(function: NeonValue): AppComplete =
    { error, result -> function.call(error, result) }

private fun rhinoComplete(function: org.mozilla.javascript.Function): AppComplete =
    { error, result ->
        val cx = RhinoContext.enter()
        try {
            val scope = ScriptableObject.getTopLevelScope(function)
            function.call(cx, scope, scope, arrayOf(RhinoContext.javaToJS(error, scope), RhinoContext.javaToJS(result, scope)))
        } finally {
            RhinoContext.exit()
        }
    }

private const val NAME_DELAYED = "App.runDelayed"
private const val NAME_SCRIPT  = "App.runOnScriptThread"
private const val NAME_UI      = "App.runOnUiThread"

private val MAIN by lazy { Handler(Looper.getMainLooper()) }

/** How a task ended: the error it threw, or what it returned. */
private class Outcome(val result: Any?, val error: Throwable?)

private fun attempt(task: AppTask): Outcome =
    try {
        Outcome(task(), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Outcome(null, e)
    }

/** Tells [onComplete] of [outcome], or throws the error to the project's error path when there is nobody to tell. */
private fun complete(outcome: Outcome, onComplete: AppComplete?) {
    if (onComplete != null)
        onComplete(outcome.error, outcome.result)
    else if (outcome.error != null)
        throw outcome.error
}

private fun failure(name: String): (String) -> String = { error ->
    translate {
        Locale.ENGLISH { "$name's callback failed: $error" }
        Locale.KOREAN  { "$name 콜백에서 오류가 났어요: $error" }
    }
}

/**
 * The delay `runDelayed` takes from what a script passed: a number cut to a whole one, a whole
 * number written as text parsed, and anything else (`null`, `undefined`, a boolean, other text) as 0.
 *
 * @throws IllegalArgumentException `"delayMillis must be non-negative"` below 0.
 */
internal fun appDelayOf(value: Any?): Long {
    val delay = ScriptArgs.long(value, "delayMillis", 0L)
    require(delay >= 0) { "delayMillis must be non-negative" }
    return delay
}

@Suppress("unused")
class AppApi: Api<AppApi.App>() {

    /**
     * MessengerBot R's `App`: the app's Android context, and running code on the script's thread or the main thread; a
     * global in API2 projects.
     *
     * Call its members on `App`, as `App.getContext()`. The functions below run [task] with no arguments, and an error
     * [task] throws isn't thrown at the caller of the function: it goes to `onComplete` when there is one, and
     * otherwise to the project's error log. Nothing runs once the project has been compiled again since the call.
     * A function that isn't one throws `"function must be a function"` at once.
     */
    class App(
        private val project: Project
    ) {

        /**
         * StarLight's application `Context`, for Android APIs that need one.
         *
         * @see guide:engines/neonjs#java-access
         */
        fun getContext(): Context = GlobalApplication.requireContext()

        /** This project's name, as the other APIs call a script's name. */
        fun getScriptName(): String =
            project.info.name

        /** Whether the calling code runs on Android's main (UI) thread. */
        fun isMainThread(): Boolean =
            Looper.getMainLooper().thread === Thread.currentThread()

        /**
         * Runs [task] on this project's script thread after [delayMillis] milliseconds.
         *
         * It returns at once and gives no way to cancel. The task is called with no arguments and its result is
         * dropped; it may start late when the project's other code is busy. It doesn't run on the main thread.
         *
         * @param task The function to run.
         * @param delayMillis The wait, in milliseconds: a number is cut to a whole one, and `null`, `undefined` or
         * text that isn't a whole number count as 0, so that the task still runs on the script thread, right away.
         * Leaving it out is the same as 0.
         * @throws IllegalArgumentException `"function must be a function"` when [task] isn't one;
         * `"delayMillis must be non-negative"` when the delay is below 0.
         */
        @JvmOverloads
        fun runDelayed(task: AppTask?, delayMillis: Any? = null) {
            val function = requireTask(task)
            val delay = appDelayOf(delayMillis)
            val version = ScriptVersion.of(project)
            ScriptTimers.schedule(delay) {
                project.runCallback(NAME_DELAYED, failure(NAME_DELAYED)) {
                    if (ScriptVersion.isCurrent(project, version))
                        function()
                }
            }
        }

        /**
         * Runs [task] on this project's script thread; the same as `runOnScriptThread`.
         *
         * @param task The function to run, with no arguments.
         * @param onComplete Called afterwards on the script thread with `(error, result)`; see `runOnScriptThread`.
         * @throws IllegalArgumentException `"function must be a function"` when [task] isn't one;
         * `"onComplete must be a function or null"` when [onComplete] is anything else.
         */
        @JvmOverloads
        fun runOnBackgroundThread(task: AppTask?, onComplete: Any? = null) =
            runOnScriptThread(task, onComplete)

        /**
         * Runs [task] on this project's script thread, where its other callbacks run, then calls [onComplete] there.
         *
         * It returns at once; the task runs later, with no arguments. [onComplete] is called right after it with
         * `(error, result)`: `null` and what [task] returned, or the error [task] threw and `null`. An error thrown in
         * [onComplete], or in [task] when there is no [onComplete], goes to the project's error log.
         *
         * @param task The function to run.
         * @param onComplete Optional; `null` or `undefined` counts as none.
         * @throws IllegalArgumentException `"function must be a function"` when [task] isn't one;
         * `"onComplete must be a function or null"` when [onComplete] is anything else. Both are thrown before
         * anything is posted.
         */
        @JvmOverloads
        fun runOnScriptThread(task: AppTask?, onComplete: Any? = null) {
            val function = requireTask(task)
            val callback = completeOf(onComplete)
            val version = ScriptVersion.of(project)
            project.runCallback(NAME_SCRIPT, failure(NAME_SCRIPT)) {
                if (ScriptVersion.isCurrent(project, version))
                    complete(attempt(function), callback)
            }
        }

        /**
         * Runs [task] on Android's main (UI) thread, then calls [onComplete] on the script thread.
         *
         * It returns at once; the task is always posted to the main thread's queue, even when called from it, and runs
         * later with no arguments. [onComplete] is not called on the main thread: it runs on the project's script
         * thread with `(error, result)`: `null` and what [task] returned, or the error [task] threw and `null`.
         * Without [onComplete], an error [task] throws goes to the project's error log. Long work in [task] freezes
         * StarLight's screens.
         *
         * In NeonJS, a script's code runs on one thread at a time: [task] waits until the script's other code has
         * finished, and StarLight's screens don't respond meanwhile.
         *
         * @param task The function to run.
         * @param onComplete Optional; `null` or `undefined` counts as none.
         * @throws IllegalArgumentException `"function must be a function"` when [task] isn't one;
         * `"onComplete must be a function or null"` when [onComplete] is anything else. Both are thrown before
         * anything is posted.
         */
        @JvmOverloads
        fun runOnUiThread(task: AppTask?, onComplete: Any? = null) {
            val function = requireTask(task)
            val callback = completeOf(onComplete)
            val version = ScriptVersion.of(project)
            MAIN.post {
                if (!ScriptVersion.isCurrent(project, version))
                    return@post
                val outcome = attempt(function)
                project.runCallback(NAME_UI, failure(NAME_UI)) {
                    if (ScriptVersion.isCurrent(project, version))
                        complete(outcome, callback)
                }
            }
        }
    }

    override val name: String = "App"

    override val objects: List<ApiObject> =
        getApiObjects<App>()

    override val instanceClass: Class<App> = App::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = App(project)
}
