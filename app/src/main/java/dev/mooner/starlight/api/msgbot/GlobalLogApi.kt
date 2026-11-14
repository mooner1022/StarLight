/*
 * GlobalLogApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import android.widget.Toast
import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val globalLogger = LoggerFactory.logger("GlobalLog")

class GlobalLogApi: Api<GlobalLogApi.GlobalLog>() {

    /**
     * StarLight's own log, the one the app's Logs tab shows, as a global named `GlobalLog` in MessengerBot API2 and
     * legacy projects (not StarLight-original ones).
     *
     * Where `Log` writes to the project's log, `GlobalLog` writes to the app-wide one, shared by every project and the
     * app itself. Each method takes the data to log and, optionally, whether to show it as a toast too:
     *
     * ```js
     * GlobalLog.i("started");           // info
     * GlobalLog.d("state", true);       // debug, and a toast
     * GlobalLog.e("failed", false);     // error, without the toast an error shows by default
     * ```
     *
     * The data may be any value: it is written as text (`null` and `undefined` as `null`, an array as `[1, 2]`). The
     * second argument is a boolean; the strings `"true"` and `"false"` and numbers are read as one too, and `null`,
     * `undefined` and any other string leave the default. Another kind of value throws an error.
     *
     * Entries are logged under the tag `GlobalLog`. A toast stays on screen for a long time and shows on the main thread.
     */
    class GlobalLog internal constructor() {

        /**
         * Deletes the entries of the app-wide log, as far as the app keeps them.
         *
         * Logs of projects are not touched; `Log.clear()` clears a project's own.
         */
        fun clear() {
            LogCollector.clearBuffer()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LogCollector.clearLogFiles()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * Logs [data] at the debug level; the same as `debug`.
         *
         * @param data The value to log.
         */
        fun d(data: Any?) = write(LogType.DEBUG, data, null, false, false)

        /**
         * Logs [data] at the debug level, showing a toast when [showToast] is `true`.
         *
         * @param data The value to log.
         * @param showToast Whether to also show a toast; `false` when `null` or `undefined`.
         * @throws IllegalArgumentException When [showToast] is an object or another value that is not a boolean.
         */
        fun d(data: Any?, showToast: Any?) = write(LogType.DEBUG, data, showToast, true, false)

        /**
         * Logs [data] at the debug level, no toast.
         *
         * @param data The value to log.
         */
        fun debug(data: Any?) = d(data)

        /**
         * Logs [data] at the debug level, showing a toast when [showToast] is `true`.
         *
         * @param data The value to log.
         * @param showToast Whether to also show a toast; `false` when `null` or `undefined`.
         * @throws IllegalArgumentException When [showToast] is an object or another value that is not a boolean.
         */
        fun debug(data: Any?, showToast: Any?) = d(data, showToast)

        /**
         * Logs [data] at the error level, with a toast.
         *
         * @param data The value to log.
         */
        fun e(data: Any?) = write(LogType.ERROR, data, null, false, true)

        /**
         * Logs [data] at the error level, with a toast unless [showToast] is `false`.
         *
         * @param data The value to log.
         * @param showToast Whether to also show a toast; `true` when `null` or `undefined`.
         * @throws IllegalArgumentException When [showToast] is an object or another value that is not a boolean.
         */
        fun e(data: Any?, showToast: Any?) = write(LogType.ERROR, data, showToast, true, true)

        /**
         * Logs [data] at the error level, with a toast; the same as `e`.
         *
         * @param data The value to log.
         */
        fun error(data: Any?) = e(data)

        /**
         * Logs [data] at the error level, with a toast unless [showToast] is `false`; the same as `e`.
         *
         * @param data The value to log.
         * @param showToast Whether to also show a toast; `true` when `null` or `undefined`.
         * @throws IllegalArgumentException When [showToast] is an object or another value that is not a boolean.
         */
        fun error(data: Any?, showToast: Any?) = e(data, showToast)

        /**
         * Logs [data] at the info level, no toast.
         *
         * @param data The value to log.
         */
        fun i(data: Any?) = write(LogType.INFO, data, null, false, false)

        /**
         * Logs [data] at the info level, showing a toast when [showToast] is `true`.
         *
         * @param data The value to log.
         * @param showToast Whether to also show a toast; `false` when `null` or `undefined`.
         * @throws IllegalArgumentException When [showToast] is an object or another value that is not a boolean.
         */
        fun i(data: Any?, showToast: Any?) = write(LogType.INFO, data, showToast, true, false)

        /**
         * Logs [data] at the info level, no toast; the same as `i`.
         *
         * @param data The value to log.
         */
        fun info(data: Any?) = i(data)

        /**
         * Logs [data] at the info level, showing a toast when [showToast] is `true`; the same as `i`.
         *
         * @param data The value to log.
         * @param showToast Whether to also show a toast; `false` when `null` or `undefined`.
         * @throws IllegalArgumentException When [showToast] is an object or another value that is not a boolean.
         */
        fun info(data: Any?, showToast: Any?) = i(data, showToast)

        /**
         * Logs [data] at the info level, no toast; the same as `i`.
         *
         * @param data The value to log.
         */
        fun log(data: Any?) = i(data)

        /**
         * Logs [data] at the info level, showing a toast when [showToast] is `true`; the same as `i`.
         *
         * @param data The value to log.
         * @param showToast Whether to also show a toast; `false` when `null` or `undefined`.
         * @throws IllegalArgumentException When [showToast] is an object or another value that is not a boolean.
         */
        fun log(data: Any?, showToast: Any?) = i(data, showToast)

        private fun write(type: LogType, data: Any?, showToast: Any?, hasToastArg: Boolean, defaultToast: Boolean) {
            val toast = if (hasToastArg) ScriptArgs.boolean(showToast, "showToast", defaultToast) else defaultToast
            val message = ScriptArgs.stringify(data)
            globalLogger.log(type) { message }
            if (toast)
                showLogToast(message)
        }

        override fun toString(): String =
            "GlobalLog"
    }

    override val name: String = "GlobalLog"

    override val objects: List<ApiObject> =
        getApiObjects<GlobalLog>()

    override val instanceClass: Class<GlobalLog> =
        GlobalLog::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        GlobalLog()
}

/** Shows [text] as a long toast on the main thread, as `Log` and `GlobalLog` do when asked. */
internal fun showLogToast(text: String) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            Toast.makeText(GlobalApplication.requireContext(), text, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/** What one call of a `Log` method asks for, once its arguments are read. */
internal class LogRequest(val tag: String?, val message: String, val showToast: Boolean)

/** Reads the arguments of the `Log` methods. */
internal object LogArgs {

    private fun isText(value: Any?): Boolean =
        value is CharSequence || (value is NeonValue && value.isString)

    /**
     * Reads `(data)` and `(data, second)` of a `Log` method.
     *
     * A text [second] is the message and [first] its tag, as StarLight's `Log.i(tag, message)` has it. Any other
     * [second] is the toast switch: a boolean, a number (`0` is false), the strings `"true"` and `"false"`, or
     * `null` and `undefined` for [defaultToast].
     *
     * @throws IllegalArgumentException When [second] is not text and can't be a boolean.
     */
    fun read(first: Any?, second: Any?, hasSecond: Boolean, defaultToast: Boolean): LogRequest {
        if (!hasSecond)
            return LogRequest(null, ScriptArgs.stringify(first), defaultToast)
        if (isText(second))
            return LogRequest(ScriptArgs.stringOrNull(first), ScriptArgs.stringify(second), false)
        val toast = ScriptArgs.boolean(second, "showToast", defaultToast)
        return LogRequest(null, ScriptArgs.stringify(first), toast)
    }
}

/** How the engine globals (`setTimeout`, `toast`, `repeat`, ...) read their arguments. */
internal object GlobalArgs {

    /**
     * A whole number from what a script passed as a delay, count or duration: a number is cut to a whole one, text
     * must be a whole number (`"100"`), `true` is 1 and `false` 0. Anything else, `null` and `undefined` included,
     * is 0.
     */
    fun convertToInt(value: Any?): Int {
        val raw: Any? = if (value is NeonValue) when {
            value.isBoolean -> value.asBoolean()
            value.isNumber  -> value.asDouble()
            value.isString  -> value.asString()
            else            -> null
        } else value
        return when (raw) {
            is Boolean      -> if (raw) 1 else 0
            is Int          -> raw
            is Number       -> raw.toDouble().let { if (it.isNaN()) 0 else it.toInt() }
            is CharSequence -> raw.toString().toIntOrNull() ?: 0
            else            -> 0
        }
    }
}
