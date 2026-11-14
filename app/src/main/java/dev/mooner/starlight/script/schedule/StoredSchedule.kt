/*
 * StoredSchedule.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.schedule

import dev.mooner.starlight.languages.ScriptJson
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * One compiled version's side of the stored tasks (`Schedule.at` and `Schedule.on`): the handlers it registered, and a
 * task in [Schedules] for each kept task that has one. The kept tasks outlive it; compiling again drops it with its
 * tasks in [Schedules], and the new version registers its handlers again.
 *
 * A kept task runs at most once: it is taken out of the file before its handler is called.
 */
internal class StoredSchedule(
    private val owner: Schedules.Owner,
    private val tasks: StoredTasks,
    private val json: ScriptJson,
    /** Whether kept tasks may run yet: in the app, once rooms were restored after the start (`RoomRestore`). */
    private val settled: () -> Boolean,
) {

    private val handlers: MutableMap<String, (data: Any?, run: Schedules.Run) -> Unit> = ConcurrentHashMap()

    /** This version's tasks in [Schedules], by the ID of the kept task each runs. */
    private val scheduled: MutableMap<String, Schedules.Task> = ConcurrentHashMap()

    private val lock = Any()

    /**
     * Registers the handler of the tasks named [name], in place of an earlier one, and sets up the kept tasks of that
     * name.
     *
     * @throws IllegalArgumentException When [name] can't be a task's name.
     */
    fun on(name: String, handler: (data: Any?, run: Schedules.Run) -> Unit) {
        checkName(name)
        handlers[name] = handler
        for (entry in tasks.list())
            if (entry.name == name)
                schedule(entry)
    }

    /**
     * Keeps a task named [name] for [time], with [data] as JSON, and sets it up when its handler is registered.
     *
     * @throws IllegalArgumentException When [name] can't be a task's name, or [data] is over [MAX_DATA_BYTES] as JSON.
     * @throws IllegalStateException When the project keeps [StoredTasks.MAX_STORED] tasks already.
     */
    fun at(time: Instant, name: String, data: Any?): StoredTasks.Entry {
        checkName(name)
        // undefined, as a function, is kept as null.
        val text = json.stringify(data) ?: "null"
        val size = text.toByteArray(Charsets.UTF_8).size
        require(size <= MAX_DATA_BYTES) {
            "Schedule.at keeps data of up to 16 KiB as JSON: this is $size bytes"
        }
        val entry = tasks.add(name, time.toEpochMilli(), text)
        if (handlers.containsKey(name))
            schedule(entry)
        else
            owner.note("No handler for '$name' yet: register one with Schedule.on")
        return entry
    }

    /** Removes every kept task named [name]; how many there were. */
    fun cancel(name: String): Int {
        val removed = tasks.removeByName(name)
        for (entry in removed)
            scheduled.remove(entry.id)?.let(Schedules::cancel)
        return removed.size
    }

    /** Removes [entry]; whether it was still kept. */
    fun cancel(entry: StoredTasks.Entry): Boolean {
        val removed = tasks.remove(entry.id)
        scheduled.remove(entry.id)?.let(Schedules::cancel)
        return removed
    }

    fun list(): List<StoredTasks.Entry> =
        tasks.list()

    /** Whether [entry] is still kept: it hasn't run, nor been cancelled. */
    fun isActive(entry: StoredTasks.Entry): Boolean =
        tasks.list().any { it.id == entry.id }

    private fun schedule(entry: StoredTasks.Entry) {
        synchronized(lock) {
            if (entry.id in scheduled)
                return
            scheduled[entry.id] = Schedules.add(owner, ScheduleSpec.Once(Instant.ofEpochMilli(entry.at)), "Schedule.at",
                stored = true, gate = settled) { run -> fire(entry, run) }
        }
    }

    private fun fire(entry: StoredTasks.Entry, run: Schedules.Run) {
        try {
            // Out of the file first: a run that dies with the app isn't made again. Gone already: cancelled meanwhile.
            if (!tasks.remove(entry.id))
                return
        } finally {
            // Its task in Schedules is used up: one the file still keeps, as when writing it failed, is set up again
            // by the next Schedule.on.
            scheduled.remove(entry.id)
        }
        // The handler as it is now, as Schedule.on may have replaced it.
        val handler = handlers[entry.name]
            ?: return
        handler(json.parse(entry.dataText), run)
    }

    companion object {
        /** The most a task's data may take, as JSON in UTF-8. */
        const val MAX_DATA_BYTES = 16 * 1024

        private val NAME = Regex("""^[\p{L}\p{N}_.\-]{1,64}$""")

        fun checkName(name: String) =
            require(NAME.matches(name)) {
                "A stored task's name is 1 to 64 letters, digits, '_', '.' or '-': '$name' isn't one"
            }
    }
}
