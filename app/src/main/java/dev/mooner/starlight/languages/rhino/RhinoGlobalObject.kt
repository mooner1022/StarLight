/*
 * RhinoGlobalObject.kt created by Minki Moon(mooner1022) on 9/3/23, 4:56 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.rhino

import android.widget.Toast
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.project.JobLocker
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.withProject
import kotlinx.coroutines.*
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.annotations.JSFunction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private typealias TimeoutID = Int

class RhinoGlobalObject(
    context: Context,
    private val project: Project
): ImporterTopLevel(context) {

    private val timeouts: ConcurrentMap<TimeoutID, Job> = ConcurrentHashMap()
    private val timeoutScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    override fun getClassName(): String {
        return "global"
    }

    @JSFunction
    fun toast(message: String, duration: Any?) {
        val mDuration = duration.getOrDefault(Toast.LENGTH_LONG)

        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(GlobalApplication.requireContext(), message, mDuration).show()
        }
    }

    @JSFunction
    fun setInterval(callback: Function, delay: Any?): TimeoutID {
        //val key = JobLocker.withProject(project).requestLock()
        val mDelay = delay.getOrThrow<Long>()

        val id = generateID()
        val job = timeoutScope.launch(start = CoroutineStart.LAZY) {
            while (true) {
                delay(mDelay)
                withContext { context ->
                    val scope = this@RhinoGlobalObject
                    callback.call(context, scope, scope, emptyArray())
                }
            }
        }.apply {
            invokeOnCompletion {
                if (id in timeouts)
                    timeouts -= id
                //JobLocker.withProject(project).tryRelease(key)
            }
        }
        timeouts[id] = job
        job.start()
        return id
    }

    @JSFunction
    fun setTimeout(callback: Function, delay: Any?): TimeoutID {
        val key = JobLocker.withProject(project).requestLock()
        val mDelay = delay.getOrThrow<Long>()

        val id = generateID()
        val job = timeoutScope.launch(start = CoroutineStart.LAZY) {
            delay(mDelay)
            withContext { context ->
                val scope = this@RhinoGlobalObject
                callback.call(context, scope, scope, emptyArray())
            }
        }.apply {
            invokeOnCompletion {
                if (id in timeouts)
                    timeouts -= id
                JobLocker.withProject(project).tryRelease(key)
            }
        }
        timeouts[id] = job
        job.start()
        return id
    }

    @JSFunction
    fun clearInterval(timeoutID: TimeoutID) =
        clearTimeout(timeoutID)

    @JSFunction
    fun clearTimeout(timeoutID: TimeoutID) {
        if (timeoutID !in timeouts)
            return
        timeouts[timeoutID]!!.cancel()
        timeouts -= timeoutID
    }

    private fun enterContext(): Context =
        (project.getLanguage() as JSRhino).enterContext()

    private fun withContext(block: (context: Context) -> Unit) {
        val context = enterContext()
        try {
            block(context)
        } finally {
            Context.exit()
        }
    }

    private fun generateID(): TimeoutID =
        (0 .. Int.MAX_VALUE).random()
            .let { if (it in timeouts) generateID() else it }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any?.getOrThrow(): T {
        return if (Undefined.isUndefined(this))
            throw IllegalArgumentException("Illegal argument type: ${this?.javaClass?.name}")
        else
            this as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any?.getOrDefault(default: T): T {
        return if (Undefined.isUndefined(this))
            default
        else
            this as T
    }

    init {
        val prototypes = javaClass.methods
            .filter { it.isAnnotationPresent(JSFunction::class.java) }

        for (proto in prototypes)
            defineFunctionProperties(arrayOf(proto.name), javaClass, 0)
    }
}