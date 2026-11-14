/*
 * LocalLogger.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.logger.internal.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private typealias ListenerCallback = (log: LogData) -> Unit

/**
 * The project's log, kept as [logs]; scripts reach it as the `Log` global, whose methods also take MessengerBot's
 * `Log.i(data, showToast)`: with a boolean second argument it shows a toast, and `Log.e` shows one by default.
 *
 * Kotlin and Java callers use the methods here. Each takes a message, or a tag and a message, at a level, by letter and
 * by name: `v`/`verbose`, `d`/`debug`, `i`/`info`, `w`/`warn`, `e`/`error`, and `wtf` (critical).
 *
 * Where an entry goes:
 * - Android's logcat, at every level.
 * - The app's Logs tab, at every level but verbose, which shows only while the developer setting **내부 로그 표시** is
 *   on.
 * - The project's own log, at every level but debug: [logs], its `logs.json`, the editor's log panel and
 *   `get_project_logs`. It keeps about the latest 50 entries, across restarts.
 * - While the app is open, warnings, errors and critical entries also pop up as an alert.
 *
 * NeonJS's `console` writes to this log too, without a tag.
 *
 * @see guide:engines/neonjs#console
 */
class ProjectLogger private constructor(
    private var _logs: MutableList<LogData>, // LinkedList
    private val file: File
) {

    private val listenerId: AtomicInteger = AtomicInteger(0)
    private val listeners: MutableMap<Int, ListenerCallback> = ConcurrentHashMap()

    /**
     * The entries kept in the project's log, oldest first: about the latest 50, without debug entries.
     *
     * It is the live list StarLight adds to, so take a copy with `Array.from(Log.logs)` to work on, and use [clear]
     * rather than changing it. Entries written before the app restarted are included.
     */
    val logs: MutableList<LogData>
        get() = this._logs

    /** Deletes every entry of the project's log, in [logs] and in its `logs.json`. */
    fun clear() {
        synchronized(_logs) {
            _logs.clear()
        }
        flush()
    }

    /**
     * Logs [message] at the verbose level, the lowest.
     *
     * Verbose entries are kept in the project's log, but show in the app's Logs tab only with the developer setting
     * **내부 로그 표시**.
     *
     * @param message The text to log.
     */
    fun v(message: String) =
        v(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the verbose level, the lowest.
     *
     * Verbose entries are kept in the project's log, but show in the app's Logs tab only with the developer setting
     * **내부 로그 표시**.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun v(tag: String?, message: String) =
        log(LogType.VERBOSE, tag, message)

    /**
     * Logs [message] at the verbose level; the same as `v`.
     *
     * @param message The text to log.
     */
    fun verbose(message: String) =
        v(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the verbose level; the same as `v`.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun verbose(tag: String?, message: String) =
        v(tag = tag, message = message)

    /**
     * Logs [message] at the debug level.
     *
     * Debug entries aren't kept in the project's log: they reach logcat and the app's Logs tab only, so [logs], the
     * editor's log panel and `get_project_logs` don't show them. NeonJS's `console.debug` logs at this level too. Use
     * [i] for output to read back later.
     *
     * @param message The text to log.
     */
    fun d(message: String) =
        d(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the debug level.
     *
     * Debug entries aren't kept in the project's log: they reach logcat and the app's Logs tab only, so [logs], the
     * editor's log panel and `get_project_logs` don't show them.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun d(tag: String?, message: String) =
        log(LogType.DEBUG, tag, message)

    /**
     * Logs [message] at the debug level; the same as `d`.
     *
     * @param message The text to log.
     */
    fun debug(message: String) =
        d(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the debug level; the same as `d`.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun debug(tag: String?, message: String) =
        d(tag = tag, message = message)

    /**
     * Logs [message] at the info level.
     *
     * @param message The text to log.
     */
    fun i(message: String) =
        i(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the info level.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun i(tag: String?, message: String) =
        log(LogType.INFO, tag, message)

    /**
     * Logs [message] at the info level; the same as `i`.
     *
     * @param message The text to log.
     */
    fun info(message: String) =
        i(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the info level; the same as `i`.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun info(tag: String?, message: String) =
        i(tag = tag, message = message)

    /**
     * Logs [message] at the warning level.
     *
     * While the app is open, it also pops up as an alert.
     *
     * @param message The text to log.
     */
    fun w(message: String) =
        w(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the warning level.
     *
     * While the app is open, it also pops up as an alert.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun w(tag: String?, message: String) =
        log(LogType.WARN, tag, message)

    /**
     * Logs [message] at the warning level; the same as `w`.
     *
     * @param message The text to log.
     */
    fun warn(message: String) =
        w(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the warning level; the same as `w`.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun warn(tag: String?, message: String) =
        w(tag = tag, message = message)

    /**
     * Logs [message] at the error level.
     *
     * While the app is open, it also pops up as an alert.
     *
     * @param message The text to log.
     */
    fun e(message: String) =
        e(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the error level.
     *
     * While the app is open, it also pops up as an alert.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun e(tag: String?, message: String) =
        log(LogType.ERROR, tag, message)

    /**
     * Logs [message] at the error level; the same as `e`.
     *
     * @param message The text to log.
     */
    fun error(message: String) =
        e(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the error level; the same as `e`.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun error(tag: String?, message: String) =
        e(tag = tag, message = message)

    // What a Terrible Failure!
    /**
     * Logs [message] at the critical level, for failures worse than an error.
     *
     * It shows like an error, and pops up as an alert while the app is open. The name is Android's "What a Terrible
     * Failure".
     *
     * @param message The text to log.
     */
    fun wtf(message: String) =
        wtf(tag = null, message = message)

    /**
     * Logs [message] under [tag] at the critical level, for failures worse than an error.
     *
     * It shows like an error, and pops up as an alert while the app is open.
     *
     * @param tag A label shown with the entry, such as a feature's name; `null` for none.
     * @param message The text to log.
     */
    fun wtf(tag: String?, message: String) =
        log(LogType.CRITICAL, tag, message)

    /**
     * Calls [listener] with each new entry, at every level, until [removeOnLogCreateListener] removes it.
     *
     * The listener runs on the thread that logs, before the entry is kept; logging from it calls it again. It stays
     * across compiles, so a script's listener would outlive the script; StarLight's editor uses it for its live log
     * panel, and plugins may too.
     *
     * @suppress
     * @param listener Called with each entry.
     * @return The ID to remove the listener with.
     */
    fun addOnLogCreateListener(listener: ListenerCallback): Int {
        val id = listenerId.getAndIncrement()
        listeners[id] = listener
        return id
    }

    /**
     * Removes the listener that [addOnLogCreateListener] returned [id] for; an unknown ID does nothing.
     *
     * @suppress
     * @param id The ID [addOnLogCreateListener] returned.
     */
    fun removeOnLogCreateListener(id: Int) {
        listeners -= id
    }

    private fun log(type: LogType, tag: String?, message: String) {
        Logger.log(type, tag, message)

        val data = LogData(
            type = type,
            tag = tag,
            message = message
        )
        for ((_, listener) in listeners)
            listener(data)
        if (data.type != LogType.DEBUG) {
            synchronized(_logs) {
                if (_logs.size > LOGS_MAX_SIZE)
                    _logs.removeAt(0)
                _logs += data
                flush()
            }
        }
    }

    private fun flush() {
        try {
            flowOf(json.encodeToString(logs))
                .onEach(file::writeText)
                .catch { it.printStackTrace() }
                .launchIn(logFlushScope)
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.v("ProjectLogger", "Failed to flush project log: $e")
        }
    }

    companion object {
        private const val LOGS_MAX_SIZE = 50

        private val logFlushScope: CoroutineScope =
            CoroutineScope(Dispatchers.IO.limitedParallelism(1)) + SupervisorJob()

        fun create(directory: File): ProjectLogger {
            directory.mkdirs()
            val file = File(directory, "logs.json")
            file.createNewFile()
            return ProjectLogger(LinkedList(), file)
        }

        fun fromFile(file: File): ProjectLogger {
            if (!file.isFile || !file.exists()) {
                throw IllegalArgumentException("Unable to open file ${file.name}")
            }
            val raw = file.readText()
            if (raw.isBlank()) return create(file.parentFile!!)
            var logs: MutableList<LogData> = try {
                json.decodeFromString(raw) // No serializer for linked list
            } catch (e: Exception) {
                e.printStackTrace()
                file.delete()
                Logger.e(ProjectLogger::class.simpleName!!, "Failed to parse log file ${file.name}, removing file")
                mutableListOf()
            }
            if (logs.size > LOGS_MAX_SIZE)
                logs = logs.drop(logs.size - LOGS_MAX_SIZE).toMutableList()
            return ProjectLogger(LinkedList(logs), file)
        }
    }
}