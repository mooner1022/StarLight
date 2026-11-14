package dev.mooner.starlight.api.original

import dev.mooner.starlight.api.msgbot.LogArgs
import dev.mooner.starlight.api.msgbot.showLogToast
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.ProjectLogger
import dev.mooner.starlight.plugincore.project.Project

@Suppress("unused")
class ProjectLoggerApi: Api<ProjectLoggerApi.ProjectLog>() {

    /**
     * The project's log, a global named `Log` in every API level: `Log.i(data)`, `Log.i(data, showToast)` or
     * `Log.i(tag, message)`.
     *
     * There is a method for each level, by letter and by name: `v`/`verbose`, `d`/`debug`, `i`/`info`/`log`,
     * `w`/`warn`, `e`/`error`, and `wtf` (critical). Each reads its second argument by what it is:
     * - **Text** is the message and the first argument its tag: `Log.i("net", "connected")`.
     * - **A boolean** says whether to show the first argument as a toast too, as in MessengerBot:
     *   `Log.i("saved", true)`. A number counts as one (`0` is false). `null` and `undefined` leave the default. A string
     *   is never a toast switch, even `"true"`: it is the message.
     * - Anything else, such as an object, throws an error.
     *
     * Without a second argument, or with a boolean, the first may be any value: it is logged as text (`null` and
     * `undefined` as `null`, an array as `[1, 2]`). A toast is not shown unless asked for, except by `e` and `error`,
     * which show one by default: pass `false` to leave it out. The toast is long and shows on the main thread. With
     * the tag form there is never a toast.
     *
     * Where an entry goes:
     * - Android's logcat, at every level.
     * - The app's Logs tab, at every level but verbose, which shows only while the developer setting **내부 로그 표시** is
     *   on.
     * - The project's own log, at every level but debug: [logs], its `logs.json`, the editor's log panel and
     *   `get_project_logs`. It keeps about the latest 50 entries, across restarts.
     * - While the app is open, warnings, errors and critical entries also pop up as an alert.
     *
     * NeonJS's `console` writes to this log too, without a tag; Rhino projects have a `console` of the same kind.
     * `GlobalLog`, a global of the MessengerBot legacy and API2 levels, writes to the app-wide log instead.
     *
     * @see guide:engines/neonjs#console
     */
    class ProjectLog internal constructor(private val logger: ProjectLogger) {

        /**
         * The entries kept in the project's log, oldest first: about the latest 50, without debug entries.
         *
         * It is the live list StarLight adds to, so take a copy with `Array.from(Log.logs)` to work on, and use [clear]
         * rather than changing it. Entries written before the app restarted are included.
         */
        val logs: MutableList<LogData>
            get() = logger.logs

        /** Deletes every entry of the project's log, in [logs] and in its `logs.json`. */
        fun clear() =
            logger.clear()

        /**
         * Logs [data] at the verbose level, the lowest.
         *
         * Verbose entries are kept in the project's log, but show in the app's Logs tab only with the developer setting
         * **내부 로그 표시**.
         *
         * @param data The value to log, as text.
         */
        fun v(data: Any?) = emit(Level.VERBOSE, data, null, false, false)

        /**
         * Logs at the verbose level: [second] is the message with [first] as its tag when it is text, and otherwise
         * whether to show [first] as a toast.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun v(first: Any?, second: Any?) = emit(Level.VERBOSE, first, second, true, false)

        /**
         * Logs [data] at the verbose level; the same as `v`.
         *
         * @param data The value to log, as text.
         */
        fun verbose(data: Any?) = v(data)

        /**
         * Logs at the verbose level; the same as `v`.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun verbose(first: Any?, second: Any?) = v(first, second)

        /**
         * Logs [data] at the debug level.
         *
         * Debug entries aren't kept in the project's log: they reach logcat and the app's Logs tab only, so [logs], the
         * editor's log panel and `get_project_logs` don't show them. NeonJS's `console.debug` logs at this level too.
         * Use [i] for output to read back later.
         *
         * @param data The value to log, as text.
         */
        fun d(data: Any?) = emit(Level.DEBUG, data, null, false, false)

        /**
         * Logs at the debug level: [second] is the message with [first] as its tag when it is text, and otherwise
         * whether to show [first] as a toast.
         *
         * Debug entries aren't kept in the project's log: they reach logcat and the app's Logs tab only.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun d(first: Any?, second: Any?) = emit(Level.DEBUG, first, second, true, false)

        /**
         * Logs [data] at the debug level; the same as `d`.
         *
         * @param data The value to log, as text.
         */
        fun debug(data: Any?) = d(data)

        /**
         * Logs at the debug level; the same as `d`.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun debug(first: Any?, second: Any?) = d(first, second)

        /**
         * Logs [data] at the info level.
         *
         * @param data The value to log, as text.
         */
        fun i(data: Any?) = emit(Level.INFO, data, null, false, false)

        /**
         * Logs at the info level: [second] is the message with [first] as its tag when it is text, and otherwise
         * whether to show [first] as a toast.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun i(first: Any?, second: Any?) = emit(Level.INFO, first, second, true, false)

        /**
         * Logs [data] at the info level; the same as `i`.
         *
         * @param data The value to log, as text.
         */
        fun info(data: Any?) = i(data)

        /**
         * Logs at the info level; the same as `i`.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun info(first: Any?, second: Any?) = i(first, second)

        /**
         * Logs [data] at the info level; the same as `i`, under MessengerBot's name for it.
         *
         * @param data The value to log, as text.
         */
        fun log(data: Any?) = i(data)

        /**
         * Logs at the info level; the same as `i`.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun log(first: Any?, second: Any?) = i(first, second)

        /**
         * Logs [data] at the warning level.
         *
         * While the app is open, it also pops up as an alert.
         *
         * @param data The value to log, as text.
         */
        fun w(data: Any?) = emit(Level.WARN, data, null, false, false)

        /**
         * Logs at the warning level: [second] is the message with [first] as its tag when it is text, and otherwise
         * whether to show [first] as a toast.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun w(first: Any?, second: Any?) = emit(Level.WARN, first, second, true, false)

        /**
         * Logs [data] at the warning level; the same as `w`.
         *
         * @param data The value to log, as text.
         */
        fun warn(data: Any?) = w(data)

        /**
         * Logs at the warning level; the same as `w`.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun warn(first: Any?, second: Any?) = w(first, second)

        /**
         * Logs [data] at the error level, and shows it as a toast.
         *
         * While the app is open, it also pops up as an alert.
         *
         * @param data The value to log, as text.
         */
        fun e(data: Any?) = emit(Level.ERROR, data, null, false, true)

        /**
         * Logs at the error level: [second] is the message with [first] as its tag when it is text, and otherwise
         * whether to show [first] as a toast, which is the default (pass `false` to leave it out).
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun e(first: Any?, second: Any?) = emit(Level.ERROR, first, second, true, true)

        /**
         * Logs [data] at the error level, and shows it as a toast; the same as `e`.
         *
         * @param data The value to log, as text.
         */
        fun error(data: Any?) = e(data)

        /**
         * Logs at the error level; the same as `e`.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun error(first: Any?, second: Any?) = e(first, second)

        // What a Terrible Failure!
        /**
         * Logs [data] at the critical level, for failures worse than an error.
         *
         * It shows like an error, and pops up as an alert while the app is open. The name is Android's "What a Terrible
         * Failure".
         *
         * @param data The value to log, as text.
         */
        fun wtf(data: Any?) = emit(Level.CRITICAL, data, null, false, false)

        /**
         * Logs at the critical level: [second] is the message with [first] as its tag when it is text, and otherwise
         * whether to show [first] as a toast.
         *
         * @param first The value to log, or the tag.
         * @param second The message, or a boolean for the toast.
         * @throws IllegalArgumentException When [second] is an object or another value that can't be a boolean.
         */
        fun wtf(first: Any?, second: Any?) = emit(Level.CRITICAL, first, second, true, false)

        private enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR, CRITICAL }

        private fun emit(level: Level, first: Any?, second: Any?, hasSecond: Boolean, defaultToast: Boolean) {
            val request = LogArgs.read(first, second, hasSecond, defaultToast)
            val tag = request.tag
            val message = request.message
            when (level) {
                Level.VERBOSE  -> logger.v(tag, message)
                Level.DEBUG    -> logger.d(tag, message)
                Level.INFO     -> logger.i(tag, message)
                Level.WARN     -> logger.w(tag, message)
                Level.ERROR    -> logger.e(tag, message)
                Level.CRITICAL -> logger.wtf(tag, message)
            }
            if (request.showToast)
                showLogToast(message)
        }

        override fun toString(): String =
            "Log"
    }

    override val name: String = "Log"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<ProjectLog> = ProjectLog::class.java

    override val objects: List<ApiObject> =
        getApiObjects<ProjectLog>()

    override fun getInstance(project: Project): Any = ProjectLog(project.logger)
}
