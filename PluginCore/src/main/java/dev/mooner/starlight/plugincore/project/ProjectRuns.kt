/*
 * ProjectRuns.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * How long a project's code holds one of its workers at a time: an event function, a timer's callback, what runs after
 * an `await`. Each stretch the worker runs without letting go is a [Run]; one that goes on past the project's threshold
 * is told to [report] then, again at [LONG_MILLIS], and once more when it ends.
 *
 * A worker held that long keeps the project's other events waiting, and in NeonJS, which runs one task at a time, every
 * one of them. It is usually a loop that doesn't end, or a call that waits: a blocking network read, `Thread.sleep`,
 * `join()` on a future.
 */
internal class ProjectRuns(
    /** The threshold, in milliseconds; 0 or less turns the warnings off. Read on each check. */
    private val thresholdMillis: () -> Long,
    private val report: (Report) -> Unit,
) {

    /** One stretch of the project's code on a worker thread. */
    class Run(val startNanos: Long) {
        /** What runs, as the warning names it: a called function's name; null when not known. */
        @Volatile
        var label: String? = null

        /** How many warnings were given: one past the threshold, two past [LONG_MILLIS]. */
        @Volatile
        var warnings: Int = 0
    }

    sealed interface Report {
        val label: String?
        val elapsedMillis: Long

        /** [label] has run for [elapsedMillis]; [first] for the warning at the threshold. */
        class Slow(override val label: String?, override val elapsedMillis: Long, val first: Boolean) : Report

        /** A run warned about ended. */
        class Ended(override val label: String?, override val elapsedMillis: Long) : Report
    }

    private val running: MutableSet<Run> = ConcurrentHashMap.newKeySet()

    private val current = ThreadLocal<Run?>()

    val isActive: Boolean
        get() = running.isNotEmpty()

    /** Runs [block] as one run, named [label] unless the code it runs names it. */
    fun <T> track(label: String?, block: () -> T): T {
        val run = Run(System.nanoTime()).also { it.label = label }
        val outer = current.get()
        current.set(run)
        running += run
        Scanner.watch(this)
        try {
            return block()
        } finally {
            running -= run
            current.set(outer)
            if (run.warnings > 0)
                report(Report.Ended(run.label, elapsedMillis(run, System.nanoTime())))
        }
    }

    /** Names the run going on on this thread, as when it calls the script's function [name]. */
    fun label(name: String) {
        current.get()?.label = name
    }

    /** Warns about the runs past a threshold at [nowNanos]. */
    fun check(nowNanos: Long) {
        val threshold = thresholdMillis()
        if (threshold <= 0)
            return
        for (run in running) {
            val elapsed = elapsedMillis(run, nowNanos)
            when {
                run.warnings == 0 && elapsed >= threshold -> {
                    run.warnings = 1
                    report(Report.Slow(run.label, elapsed, first = true))
                }
                run.warnings == 1 && threshold < LONG_MILLIS && elapsed >= LONG_MILLIS -> {
                    run.warnings = 2
                    report(Report.Slow(run.label, elapsed, first = false))
                }
            }
        }
    }

    private fun elapsedMillis(run: Run, nowNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(nowNanos - run.startNanos)

    /**
     * Checks every project with a run going on, once a second, on a thread of its own: the project's workers may all be
     * held. It stops while no project runs anything.
     */
    internal object Scanner {

        private val watched: MutableSet<ProjectRuns> = ConcurrentHashMap.newKeySet()

        private val executor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "project-runs").apply { isDaemon = true }
        }

        @Volatile
        private var scan: ScheduledFuture<*>? = null

        fun watch(runs: ProjectRuns) {
            if (!watched.add(runs) && scan != null)
                return
            synchronized(this) {
                if (scan == null)
                    scan = executor.scheduleWithFixedDelay(::checkAll, 1, 1, TimeUnit.SECONDS)
            }
        }

        private fun checkAll() {
            val now = System.nanoTime()
            for (runs in watched) {
                try {
                    runs.check(now)
                } catch (_: Exception) {
                    // A report that fails mustn't stop the scan for every project.
                }
                if (!runs.isActive) {
                    watched -= runs
                    // A run that started meanwhile has added it back, or finds it gone and adds it.
                    if (runs.isActive)
                        watched += runs
                }
            }
            synchronized(this) {
                if (watched.isEmpty()) {
                    scan?.cancel(false)
                    scan = null
                }
            }
        }
    }

    companion object {
        /** When a run still going gets its second warning. */
        const val LONG_MILLIS = 60_000L
    }
}
