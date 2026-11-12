/*
 * Schedules.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.schedule

import dev.mooner.starlight.plugincore.project.ProjectID
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every project's scheduled tasks, run when their time comes, by the wall clock.
 *
 * [runDue] runs what is due and says when to look again; a [Driver] calls it at that time, and when an alarm wakes the
 * device. A task is run only while its project is compiled and on; one due while it compiles waits for the compile,
 * one due while it is off is skipped, and one due while its last run still runs is skipped too.
 */
internal object Schedules {

    /** A project, as its tasks need it. */
    interface Owner {
        val id: ProjectID
        fun readiness(): Readiness
        /** Runs [block] as one of the project's tasks named [name], then [onEnd], however it ended. */
        fun run(name: String, block: () -> Unit, onEnd: () -> Unit)
        /** Notes [message] in the app's log, as a run skipped. */
        fun note(message: String)
    }

    enum class Readiness {
        /** Compiled and on. */
        RUN,
        /** Compiling: the task waits for it. */
        WAIT,
        /** Off, or not compiled: the run is skipped. */
        SKIP,
    }

    /** What a callback is told about its run. */
    class Run(val scheduledAt: Long, val lateMillis: Long)

    class Task(
        val owner: Owner,
        val spec: ScheduleSpec,
        /** The task's name in a warning about a run that takes long: `Schedule.daily` and so on. */
        val name: String,
        val callback: (Run) -> Unit,
    ) {
        /** When it runs next, in milliseconds since 1970; null once it is done or cancelled. */
        @Volatile
        var next: Long? = null
            internal set

        @Volatile
        var running = false
            internal set
    }

    /** Runs [Schedules.runDue] when it is due; the app's, or a test's. */
    interface Driver {
        /** Calls [runDue] soon, as a task was added or cancelled. */
        fun poke()
    }

    /** Tasks one project may have at once. */
    const val MAX_TASKS = 100

    /** How soon a task that waits for its project's compile is looked at again. */
    const val COMPILE_RETRY_MILLIS = 1_000L

    private val tasks = CopyOnWriteArrayList<Task>()

    var clock: () -> Long = System::currentTimeMillis
    var zone: () -> ZoneId = ZoneId::systemDefault

    @Volatile
    var driver: Driver? = null

    /**
     * Adds a task of [owner]'s, its first run worked out now.
     *
     * @throws IllegalStateException When [owner] has [MAX_TASKS] already.
     * @throws IllegalArgumentException When [spec] never runs.
     */
    fun add(owner: Owner, spec: ScheduleSpec, name: String, callback: (Run) -> Unit): Task {
        check(tasks.count { it.owner.id == owner.id } < MAX_TASKS) {
            "A project can have $MAX_TASKS scheduled tasks at once: cancel some first"
        }
        val first = spec.first(Instant.ofEpochMilli(clock()), zone())
            ?: throw IllegalArgumentException("'${spec.description}' never runs")
        val task = Task(owner, spec, name, callback)
        task.next = first.toEpochMilli()
        tasks += task
        driver?.poke()
        return task
    }

    /** Cancels [task]; whether it was still to run. */
    fun cancel(task: Task): Boolean {
        val wasActive = task.next != null
        task.next = null
        tasks -= task
        driver?.poke()
        return wasActive
    }

    /** Cancels every task of the project [id]; how many there were. */
    fun cancelAll(id: ProjectID): Int {
        val owned = tasks.filter { it.owner.id == id }
        owned.forEach { it.next = null }
        tasks.removeAll(owned.toSet())
        if (owned.isNotEmpty())
            driver?.poke()
        return owned.size
    }

    fun tasksOf(id: ProjectID): List<Task> =
        tasks.filter { it.owner.id == id }

    /** Runs what is due at [now]; when to look again, or null when nothing is left to run. */
    fun runDue(now: Long): Long? {
        var earliest: Long? = null
        fun consider(time: Long) {
            if (earliest == null || time < earliest!!)
                earliest = time
        }
        for (task in tasks) {
            val at = task.next ?: continue
            if (at > now) {
                consider(at)
                continue
            }
            when (task.owner.readiness()) {
                Readiness.WAIT -> {
                    consider(now + COMPILE_RETRY_MILLIS)
                    continue
                }
                Readiness.SKIP ->
                    task.owner.note("Skipped ${task.spec.description}: the project is off")
                Readiness.RUN -> {
                    if (task.running)
                        task.owner.note("Skipped ${task.spec.description}: its last run hasn't ended")
                    else {
                        task.running = true
                        // How late it starts: the project's workers may be busy, NeonJS's one with a long event.
                        task.owner.run(task.name, { task.callback(Run(scheduledAt = at, lateMillis = clock() - at)) }) {
                            task.running = false
                        }
                    }
                }
            }
            // A run missed while the device slept is made once, not once for each time it missed.
            val next = task.spec.next(Instant.ofEpochMilli(maxOf(at, now)), zone())?.toEpochMilli()
            if (task.next != null)
                task.next = next
            if (next == null)
                tasks -= task
            else
                consider(next)
        }
        return earliest
    }
}
