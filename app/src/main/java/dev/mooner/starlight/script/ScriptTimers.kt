/*
 * ScriptTimers.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The timers of the script APIs' own bookkeeping: saving a store a moment after it changed, ending a wait for an
 * answer. Script code never runs here; what these start for a script goes to the project's own workers.
 */
internal object ScriptTimers {

    private val count = AtomicInteger()

    private val executor = ScheduledThreadPoolExecutor(2, ThreadFactory { task ->
        Thread(task, "script-timers-${count.incrementAndGet()}").apply { isDaemon = true }
    }).apply {
        removeOnCancelPolicy = true
    }

    fun schedule(delayMillis: Long, task: () -> Unit): ScheduledFuture<*> =
        executor.schedule(Runnable { task() }, delayMillis, TimeUnit.MILLISECONDS)

    fun execute(task: () -> Unit) =
        executor.execute(Runnable { task() })
}
