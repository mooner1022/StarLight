/*
 * RhinoGlobalObject.kt created by Minki Moon(mooner1022) on 9/3/23, 4:56 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.rhino

import android.widget.Toast
import dev.mooner.starlight.api.msgbot.GlobalArgs
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.languages.TimerDelay
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.script.errors.UncaughtErrors
import kotlinx.coroutines.*
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

private typealias TimeoutID = Int

class RhinoGlobalObject(
    context: Context,
    private val project: Project
): ImporterTopLevel(context) {

    private val timeouts: ConcurrentMap<TimeoutID, Job> = ConcurrentHashMap()

    private val lastTimerId = AtomicInteger()

    override fun getClassName(): String {
        return "global"
    }

    /**
     * Defines `setTimeout`, `setInterval`, `clearTimeout`, `clearInterval`, `toast`, `repeat` and `console` in this
     * scope. It runs once the standard objects are in place, as they are the prototypes of what it makes.
     */
    internal fun installGlobals() {
        global("setTimeout", 2) { _, args -> startTimer(args, repeat = false) }
        global("setInterval", 2) { _, args -> startTimer(args, repeat = true) }
        global("clearTimeout", 1) { _, args -> clearTimer(args) }
        global("clearInterval", 1) { _, args -> clearTimer(args) }
        global("toast", 2) { _, args -> showToast(args) }
        global("repeat", 3) { cx, args -> repeat(cx, args) }

        val logger = project.logger
        val console = RhinoConsole(this, onClear = logger::clear) { level, text ->
            when (level) {
                RhinoConsole.Level.DEBUG -> logger.debug(text)
                RhinoConsole.Level.INFO  -> logger.info(text)
                RhinoConsole.Level.WARN  -> logger.warn(text)
                RhinoConsole.Level.ERROR -> logger.error(text)
            }
        }
        defineProperty("console", console, ScriptableObject.DONTENUM)
    }

    private fun global(name: String, arity: Int, body: (cx: Context, args: Array<Any?>) -> Any?) {
        defineProperty(name, RhinoFunction(this, name, arity, body), ScriptableObject.DONTENUM)
    }

    private fun showToast(args: Array<Any?>) {
        val message = if (args.isEmpty()) "null" else Context.toString(args[0])
        val duration = GlobalArgs.convertToInt(args.getOrNull(1))

        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(GlobalApplication.requireContext(), message, duration).show()
        }
    }

    private fun startTimer(args: Array<Any?>, repeat: Boolean): TimeoutID {
        val kind = if (repeat) "setInterval" else "setTimeout"
        if (args.isEmpty())
            throw IllegalArgumentException("$kind requires at least 2 arguments")
        val callback = args[0] as? Function
            ?: throw IllegalArgumentException("First argument must be a function")
        val mDelay = TimerDelay.effective(GlobalArgs.convertToInt(args.getOrNull(1)).toLong(), repeat)
        val params: Array<Any?> = args.copyOfRange(minOf(2, args.size), args.size)

        val id = lastTimerId.incrementAndGet()
        val job = project.launch(CoroutineName(kind), start = CoroutineStart.LAZY) {
            do {
                delay(mDelay)
                withContext { context ->
                    val scope = this@RhinoGlobalObject
                    callback.call(context, scope, scope, params)
                }?.also { e ->
                    project.logger.error("Exception on ${if (repeat) "interval" else "timeout"} $id: $e")
                    // Not when a compile replaced this version since.
                    if (project.getScope() === this@RhinoGlobalObject)
                        UncaughtErrors.report(project.info.id, e, UncaughtErrors.Origin.EXCEPTION, kind)
                }
            } while (repeat && isActive)
        }.apply {
            invokeOnCompletion {
                timeouts -= id
            }
        }
        timeouts[id] = job
        job.start()
        return id
    }

    private fun clearTimer(args: Array<Any?>) {
        val id = GlobalArgs.convertToInt(args.getOrNull(0))
        timeouts.remove(id)?.cancel()
    }

    /** `repeat(fn, count[, args])`: calls the function [count] times here and now, and gives the results as an array. */
    private fun repeat(cx: Context, args: Array<Any?>): Any? {
        if (args.size < 2)
            return Undefined.instance
        val function = args[0] as? Function
            ?: return Undefined.instance
        val count = GlobalArgs.convertToInt(args[1])
        if (count <= 0)
            return Undefined.instance
        val spread: Array<Any?> = when (val extra = args.getOrNull(2)) {
            null -> if (args.size > 2) arrayOf<Any?>(null) else emptyArray<Any?>()
            is NativeArray -> Array(extra.length.toInt()) { i ->
                ScriptableObject.getProperty(extra, i).let { if (it === org.mozilla.javascript.Scriptable.NOT_FOUND) Undefined.instance else it }
            }
            else -> arrayOf<Any?>(extra)
        }
        return try {
            val results = ArrayList<Any?>(count.coerceAtMost(1024))
            for (n in 0 until count) {
                results += try {
                    function.call(cx, this, this, spread)
                } catch (e: Exception) {
                    null
                }
            }
            cx.newArray(this, results.toTypedArray())
        } catch (e: Exception) {
            Undefined.instance
        }
    }

    private fun enterContext(): Context =
        (project.getLanguage() as JSRhino).enterContext()

    private fun withContext(block: (context: Context) -> Unit): Throwable? {
        val context = enterContext()
        return try {
            block(context)
            null
        } catch (e: Throwable) {
            e
        } finally {
            Context.exit()
        }
    }
}
