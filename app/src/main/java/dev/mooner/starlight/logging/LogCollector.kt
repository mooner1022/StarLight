/*
 * LogReceiver.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 5:44
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.logging

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

    private val logFile: File

    private val mLogs: MutableList<LogData> = mutableListOf()
    val logs: List<out LogData>
        get() = mLogs

    private suspend fun onLogCreated(event: Events.Log.Create) {
        logWriteScope.launch {
            val data = event.log

            mLogs += data

            if (data.type.priority >= LogType.DEBUG.priority) {
                try {
                    logFile.appendText("$data\n")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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

        logCollectionScope.launch {
            EventHandler.on(this, ::onLogCreated)
        }
    }
}