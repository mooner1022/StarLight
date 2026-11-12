/*
 * ScheduleApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.languages.rhino.JSRhino
import dev.mooner.starlight.listener.RoomRestore
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.script.ScriptTimes
import dev.mooner.starlight.script.runCallback
import dev.mooner.starlight.script.schedule.CronExpression
import dev.mooner.starlight.script.schedule.ScheduleSpec
import dev.mooner.starlight.script.schedule.Schedules
import dev.mooner.starlight.script.schedule.StoredSchedule
import dev.mooner.starlight.script.schedule.StoredTasks
import org.mozilla.javascript.Context
import java.time.Instant

class ScheduleApi: Api<ScheduleApi.Schedule>() {

    /**
     * Runs code at times of the day or on a cron schedule, by the device's clock and time zone; a StarLight original
     * global.
     *
     * ```js
     * // Every day at 9:00, to a room the bot has received a message from since StarLight started.
     * Schedule.daily("09:00", () => Chat.findRoom("Family")?.send("Good morning!"));
     * // On weekdays at 18:30.
     * Schedule.cron("30 18 * * 1-5", (run) => Log.i("schedule", `late by ${run.lateMillis} ms`));
     * ```
     *
     * - A task runs only while the project is compiled and on: one due while the project is off is skipped. One due
     *   while it compiles waits for the compile to end.
     * - Tasks belong to the compiled code: compiling the project again or unloading it cancels them, so set them up in
     *   the top-level code, which runs on each compile, as StarLight compiles projects that are on when it starts.
     * - Runs come on time while the device is awake. While it sleeps, Android may let them come late, as it batches
     *   alarms to save battery; the callback is told how late. A run missed while StarLight wasn't running is skipped,
     *   and several missed while the device slept are made once.
     * - A run that comes while the task's last run still runs is skipped.
     * - A callback runs as one of the project's tasks, as a timer's does: an error it throws is logged and doesn't
     *   unload the project.
     *
     * `Schedule.at` keeps a task in the project's folder instead, so that it outlives compiles and restarts, as for a
     * reminder set while the bot runs. Its handler is a function registered by name with `Schedule.on` in the top-level
     * code, which runs on each compile:
     *
     * ```js
     * Schedule.on("remind", (data, run) => Chat.findRoom(data.room)?.send(data.text));
     * export function onMessage(event) {
     *     if (event.message === "!remind")
     *         Schedule.at(Date.now() + 3600_000, "remind", { room: event.room.name, text: "An hour passed" });
     * }
     * ```
     *
     * @see guide:events#scheduled-tasks
     */
    class Schedule internal constructor(
        private val project: Project,
        private val json: ScriptJson,
    ) {

        private val owner = ProjectOwner(project)

        private val stored = StoredSchedule(owner, StoredTasks.of(project), workerJson(), RoomRestore::isSettled)

        /**
         * [json] that also works on the project's workers, where a kept task's data is read before its handler is
         * called: Rhino's JSON needs a context, which a worker enters only once the script's function is called.
         */
        private fun workerJson(): ScriptJson {
            val rhino = project.getLanguage() as? JSRhino
                ?: return json
            return json.enteringRhino(rhino::enterContext)
        }

        /**
         * Runs [callback] every day at [time], in the device's time zone.
         *
         * A time a clock change skips, as one in a daylight-saving gap, runs right after the gap.
         *
         * @param time `HH:mm` or `HH:mm:ss`, as `"09:00"` or `"21:30:15"`.
         * @param callback Given the run's `ScheduleRun`.
         * @return The task, to cancel it.
         * @throws IllegalArgumentException When [time] isn't a time of day.
         * @throws IllegalStateException When the project has 100 tasks already.
         */
        fun daily(time: String, callback: (run: ScheduleRun) -> Unit): ScheduledTask =
            add(ScheduleSpec.Daily.parse(time), "Schedule.daily", callback)

        /**
         * Runs [callback] at the times the five-field cron [expression] names, in the device's time zone.
         *
         * The fields are `minute hour day-of-month month day-of-week`, each `*`, a number, a range `a-b`, a step
         * (`*` or a range, then `/n`), or a list of these joined by commas. Months may be `JAN`…`DEC` and days of the week
         * `SUN`…`SAT`; Sunday is 0 or 7. When both day fields are restricted, a day matching either runs.
         *
         * ```js
         * Schedule.cron("0 9 * * MON-FRI", morning);   // 9:00 on weekdays
         * Schedule.cron("0,30 * * * *", halfHourly);   // every hour, on the hour and half past
         * ```
         *
         * @param expression The schedule, as `"0 9 * * 1-5"`.
         * @param callback Given the run's `ScheduleRun`.
         * @return The task, to cancel it.
         * @throws IllegalArgumentException When [expression] isn't one, or never runs.
         * @throws IllegalStateException When the project has 100 tasks already.
         */
        fun cron(expression: String, callback: (run: ScheduleRun) -> Unit): ScheduledTask =
            add(ScheduleSpec.Cron(CronExpression.parse(expression)), "Schedule.cron", callback)

        /**
         * Runs [callback] once at [time]; a time already past runs at once.
         *
         * @param time A `Date`, milliseconds since 1970 as `Date.now()` gives them, or ISO 8601 text: `"2026-12-25T09:00"`
         * is in the device's time zone, `"2026-12-25T09:00+09:00"` and `"2026-12-25T00:00Z"` name theirs.
         * @param callback Given the run's `ScheduleRun`.
         * @return The task, to cancel it.
         * @throws IllegalArgumentException When [time] is none of these.
         * @throws IllegalStateException When the project has 100 tasks already.
         */
        fun once(time: Any, callback: (run: ScheduleRun) -> Unit): ScheduledTask =
            add(ScheduleSpec.Once(instantOf(time)), "Schedule.once", callback)

        /** The project's tasks from `daily`, `cron` and `once` still to run, as an array; not those of `Schedule.at`. */
        fun getTasks(): Array<ScheduledTask> =
            Schedules.tasksOf(project.info.id).filterNot { it.stored }.map(::ScheduledTask).toTypedArray()

        /**
         * Cancels every task of the project from `daily`, `cron` and `once`. Those of `Schedule.at` stay: cancel them
         * with `Schedule.cancel(name)` or their own `cancel()`.
         *
         * @return How many were cancelled.
         */
        fun cancelAll(): Int =
            Schedules.cancelAll(project.info.id, keepStored = true)

        /**
         * Registers [handler] for the tasks `Schedule.at` keeps under [name], in place of an earlier one. Call it in
         * the top-level code: the handlers of a compiled version go with it, and a task waits, kept, until a version
         * registers its name.
         *
         * @param name As given to `Schedule.at`.
         * @param handler Given the task's data, as the engine's own value, and its `ScheduleRun`.
         * @throws IllegalArgumentException When [name] can't be a task's name.
         */
        fun on(name: String, handler: (data: Any?, run: ScheduleRun) -> Unit) =
            stored.on(name) { data, run -> handler(data, ScheduleRun(run.scheduledAt, run.lateMillis)) }

        /**
         * Keeps a task named [name] to run at [time], without data; see the form with data.
         *
         * @param time A `Date`, milliseconds since 1970 as `Date.now()` gives them, or ISO 8601 text.
         * @param name Up to 64 letters, digits, `_`, `.` or `-`; the handler `Schedule.on` registered for it runs.
         * @return The kept task.
         * @throws IllegalArgumentException When [time] or [name] isn't one.
         * @throws IllegalStateException When the project keeps 100 tasks of `Schedule.at` already.
         */
        fun at(time: Any, name: String): StoredTask =
            at(time, name, null)

        /**
         * Keeps a task named [name] to run at [time], with [data], in `data/schedule/stored.json` in the project's
         * folder: it outlives compiles and restarts, unlike the other tasks.
         *
         * - At [time] the handler `Schedule.on` registered for [name] is called with the data and the run's
         *   `ScheduleRun`. A time already past runs at once.
         * - It runs only while the project is compiled and on. One that came due meanwhile, as while StarLight wasn't
         *   running or the project was off, runs once when the project is compiled and on again, however late:
         *   `run.lateMillis` tells how late, for the handler to pass over one too old.
         * - It runs at most once: it is taken out of the file before the handler is called, so one whose run ends with
         *   the app isn't made again.
         * - After StarLight starts, a task waits up to 10 seconds for the rooms to be restored, so that
         *   `Chat.findRoom` finds them.
         * - A task whose name has no handler stays kept until one is registered.
         * - `cancelAll()` and `getTasks()` leave these tasks out: see `getStored()` and `cancel(name)`.
         *
         * @param time A `Date`, milliseconds since 1970 as `Date.now()` gives them, or ISO 8601 text: `"2026-12-25T09:00"`
         * is in the device's time zone, `"2026-12-25T09:00+09:00"` and `"2026-12-25T00:00Z"` name theirs.
         * @param name Up to 64 letters, digits, `_`, `.` or `-`; the handler `Schedule.on` registered for it runs.
         * @param data What `JSON.stringify` can write, up to 16 KiB as JSON; the handler gets a copy. NeonJS keeps
         * `undefined` and functions as `null`; Rhino hands `undefined` over as the text `"undefined"`.
         * @return The kept task.
         * @throws IllegalArgumentException When [time] or [name] isn't one, or [data] is too large.
         * @throws IllegalStateException When the project keeps 100 tasks of `Schedule.at` already.
         */
        fun at(time: Any, name: String, data: Any?): StoredTask {
            val instant = ScriptTimes.instantOf(time) ?: throw IllegalArgumentException(
                "Schedule.at takes a Date, milliseconds since 1970 or ISO 8601 text, as \"2026-12-25T09:00\"")
            return StoredTask(stored, stored.at(instant, name, data), json)
        }

        /** The tasks `Schedule.at` keeps for the project, as an array, those still waiting for a handler included. */
        fun getStored(): Array<StoredTask> =
            stored.list().map { StoredTask(stored, it, json) }.toTypedArray()

        /**
         * Cancels every task `Schedule.at` keeps under [name].
         *
         * @return How many were cancelled.
         */
        fun cancel(name: String): Int =
            stored.cancel(name)

        private fun add(spec: ScheduleSpec, name: String, callback: (run: ScheduleRun) -> Unit): ScheduledTask =
            ScheduledTask(Schedules.add(owner, spec, name) { run -> callback(ScheduleRun(run.scheduledAt, run.lateMillis)) })

        private fun instantOf(time: Any): Instant =
            ScriptTimes.instantOf(time) ?: throw IllegalArgumentException(
                "Schedule.once takes a Date, milliseconds since 1970 or ISO 8601 text, as \"2026-12-25T09:00\"")

        private val canceller = object : ProjectLifecycleObserver.ExplicitObserver {
            // Before the new code runs, which sets up its own.
            override fun onCompileStart(project: Project) {
                Schedules.cancelAll(project.info.id)
                project.getLifecycle().unregisterObserver(this)
            }

            override fun onDestroy(project: Project) {
                Schedules.cancelAll(project.info.id)
                project.getLifecycle().unregisterObserver(this)
            }

            // A kept task that came due while the project was off runs now.
            override fun onEnable(project: Project) {
                Schedules.driver?.poke()
            }
        }

        init {
            project.getLifecycle().registerObserver(canceller)
        }
    }

    /** A task from `Schedule.daily`, `cron` or `once`. */
    class ScheduledTask internal constructor(
        private val task: Schedules.Task,
    ) {

        /** When and how often it runs: `daily 09:00`, `cron 0 9 * * 1-5` or `once 2026-12-25T00:00:00Z`. */
        val description: String
            get() = task.spec.description

        /** When it runs next, in milliseconds since 1970 (`new Date(task.nextRun)`); `null` once it won't run again. */
        val nextRun: Long?
            get() = task.next

        /** Whether it will run again: `false` once it is cancelled, or a `once` task has run. */
        val active: Boolean
            get() = task.next != null

        /**
         * Cancels the task; a run already going on finishes.
         *
         * @return Whether it was still to run.
         */
        fun cancel(): Boolean =
            Schedules.cancel(task)

        override fun toString(): String =
            "ScheduledTask(${task.spec.description})"
    }

    /** A task kept by `Schedule.at`. */
    class StoredTask internal constructor(
        private val schedule: StoredSchedule,
        private val entry: StoredTasks.Entry,
        private val json: ScriptJson,
    ) {

        /** Its ID, 8 hexadecimal digits. */
        val id: String
            get() = entry.id

        /** The name its handler is registered under. */
        val name: String
            get() = entry.name

        /** When it is due, in milliseconds since 1970 (`new Date(task.time)`). */
        val time: Long
            get() = entry.at

        /** Its data, as the engine's own value: a new copy each time it is read. */
        val data: Any?
            get() = json.parse(entry.dataText)

        /** Whether it is still kept: `false` once it has run or was cancelled. */
        val active: Boolean
            get() = schedule.isActive(entry)

        /**
         * Cancels the task; a run already going on finishes.
         *
         * @return Whether it was still kept.
         */
        fun cancel(): Boolean =
            schedule.cancel(entry)

        override fun toString(): String =
            "StoredTask(${entry.name} at ${Instant.ofEpochMilli(entry.at)})"
    }

    /** What a scheduled callback is told about its run. */
    class ScheduleRun internal constructor(
        /** When the run was due, in milliseconds since 1970. */
        val scheduledAt: Long,
        /** How late it started, in milliseconds: about 0 while the device is awake. */
        val lateMillis: Long,
    )

    private class ProjectOwner(private val project: Project) : Schedules.Owner {

        override val id: ProjectID
            get() = project.info.id

        override fun readiness(): Schedules.Readiness =
            when {
                project.getLifecycle().state == ProjectLifecycle.State.COMPILING -> Schedules.Readiness.WAIT
                !project.isCompiled || !project.info.isEnabled                  -> Schedules.Readiness.SKIP
                else                                                            -> Schedules.Readiness.RUN
            }

        override fun run(name: String, block: () -> Unit, onEnd: () -> Unit) {
            project.runCallback(name, { error ->
                translate {
                    Locale.ENGLISH { "$name's callback failed: $error" }
                    Locale.KOREAN  { "$name 콜백에서 오류가 났어요: $error" }
                }
            }, block).invokeOnCompletion { onEnd() }
        }

        override fun note(message: String) =
            project.logger.d(message)
    }

    override val name: String = "Schedule"

    override val objects: List<ApiObject> =
        getApiObjects<Schedule>()

    override val instanceClass: Class<Schedule> =
        Schedule::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Schedule(project, ScriptJson.current())
}

/** This JSON, with a Rhino context entered by [enter] around [ScriptJson.parse] on a thread that has none. */
internal fun ScriptJson.enteringRhino(enter: () -> Context): ScriptJson {
    val json = this
    return object : ScriptJson by json {
        override fun parse(json: String): Any? {
            if (Context.getCurrentContext() != null)
                return this@enteringRhino.parse(json)
            enter()
            try {
                return this@enteringRhino.parse(json)
            } finally {
                Context.exit()
            }
        }
    }
}
