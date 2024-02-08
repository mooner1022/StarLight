/*
 * LogReceiver.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 5:44
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.logging

import dev.mooner.starlight.CA_DEV_MODE
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import kotlinx.coroutines.*
import java.io.File

object LogCollector {

    private val logCollectionScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logWriteScope: CoroutineScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1)) + SupervisorJob()

    private var appendInternalLog: Boolean =
        GlobalConfig
            .category(CA_DEV_MODE)
            .getBoolean("append_internal_log", false)

    private var logBufferMaxSize: Int =
        GlobalConfig
            .category("general")
            .getString("log_buffer_max_size", "100")
            .toInt()

    private val logFile: File

    private val mLogs: MutableList<LogData> = createDeque()
    val logs: List<LogData>
        get() = mLogs

    private fun createDeque(): ArrayDeque<LogData> {
        return logBufferMaxSize.let(::ArrayDeque)
    }

    private fun onLogCreated(event: Events.Log.Create) {
        logWriteScope.launch {
            val data = event.log

            try {
                if (mLogs.size >= logBufferMaxSize)
                    (mLogs as ArrayDeque).removeFirst()
                if (appendInternalLog || data.type.priority >= LogType.VERBOSE.priority)
                    mLogs += data

                if (data.type.priority >= LogType.DEBUG.priority) {
                    logFile.appendText("$data\n")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun onGlobalConfigUpdate(event: Events.Config.GlobalConfigUpdate) {
        appendInternalLog = GlobalConfig
            .category(CA_DEV_MODE)
            .getBoolean("append_internal_log", false)

        val nSize = GlobalConfig
            .category("general")
            .getString("log_buffer_max_size", "100")
            .toIntOrNull()
            ?: return
        if (mLogs.size > nSize) {
            while (mLogs.size > nSize) {
                (mLogs as ArrayDeque).removeFirst()
            }
        }
        logBufferMaxSize = nSize
    }

    init {
        val dirName = TimeUtils.formatCurrentDate("yyyy-MM-dd")
        val dir = File(getStarLightDirectory(), "logs").resolve(dirName)
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }

        val time = TimeUtils.formatCurrentDate("HH_mm_ss_SSS")
        val fileName = "$time.log"
        logFile = File(dir, fileName)

        if (!logFile.exists())
            logFile.createNewFile()

        EventHandler.apply {
            on(logCollectionScope, ::onLogCreated)
            on(logCollectionScope, ::onGlobalConfigUpdate)
        }
    }
}