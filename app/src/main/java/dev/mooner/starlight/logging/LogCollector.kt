/*
 * LogReceiver.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 5:44
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.logging

import dev.mooner.starlight.CA_LOGS
import dev.mooner.starlight.CF_LOG_BUFFER_SIZE
import dev.mooner.starlight.CF_LOG_RETENTION
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
import java.time.LocalDate

object LogCollector {

    private val logCollectionScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logWriteScope: CoroutineScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1)) + SupervisorJob()

    val retentionDays = listOf(3, 7, 30, 0)
    const val DEFAULT_RETENTION_INDEX = 2
    val bufferSizes = listOf(100, 300, 500, 1000)

    private val logDirectory = File(getStarLightDirectory(), "logs")

    private var logBufferMaxSize: Int = configuredBufferSize()

    private val logFile: File

    private val mLogs: MutableList<LogData> = createDeque()
    val logs: List<LogData>
        get() = synchronized(mLogs) { mLogs.toList() }

    private fun createDeque(): ArrayDeque<LogData> {
        return logBufferMaxSize.let(::ArrayDeque)
    }

    private fun onLogCreated(event: Events.Log.Create) {
        logWriteScope.launch {
            val data = event.log

            try {
                synchronized(mLogs) {
                    if (mLogs.size >= logBufferMaxSize)
                        (mLogs as ArrayDeque).removeAt(0)
                    mLogs += data
                }

                if (data.type.priority >= LogType.DEBUG.priority) {
                    logFile.appendText("$data\n")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun onGlobalConfigUpdate(event: Events.Config.GlobalConfigUpdate) {
        val nSize = configuredBufferSize()
        synchronized(mLogs) {
            while (mLogs.size > nSize) {
                (mLogs as ArrayDeque).removeAt(0)
            }
        }
        logBufferMaxSize = nSize
    }

    private fun configuredBufferSize(): Int =
        bufferSizes.getOrElse(GlobalConfig.category(CA_LOGS).getInt(CF_LOG_BUFFER_SIZE, 0)) { bufferSizes.first() }

    private fun deleteExpiredLogs() {
        val days = retentionDays.getOrElse(GlobalConfig.category(CA_LOGS).getInt(CF_LOG_RETENTION, DEFAULT_RETENTION_INDEX)) { 0 }
        if (days <= 0)
            return
        val oldest = LocalDate.now().minusDays(days - 1L)
        logDirectory.listFiles()?.forEach { dir ->
            val date = runCatching { LocalDate.parse(dir.name) }.getOrNull() ?: return@forEach
            if (date.isBefore(oldest))
                dir.deleteRecursively()
        }
    }

    fun logFileStats(): Pair<Int, Long> {
        val files = logDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "log" }
            .toList()
        return files.size to files.sumOf(File::length)
    }

    fun clearLogFiles() {
        logDirectory.listFiles()?.forEach { file ->
            if (file != logFile.parentFile)
                file.deleteRecursively()
            else
                file.listFiles()?.forEach { if (it != logFile) it.delete() }
        }
    }

    init {
        val dirName = TimeUtils.formatCurrentDate("yyyy-MM-dd")
        val dir = logDirectory.resolve(dirName)
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }

        val time = TimeUtils.formatCurrentDate("HH_mm_ss_SSS")
        val fileName = "$time.log"
        logFile = File(dir, fileName)

        if (!logFile.exists())
            logFile.createNewFile()

        logWriteScope.launch { deleteExpiredLogs() }

        EventHandler.apply {
            on(logCollectionScope, ::onLogCreated)
            on(logCollectionScope, ::onGlobalConfigUpdate)
        }
    }
}