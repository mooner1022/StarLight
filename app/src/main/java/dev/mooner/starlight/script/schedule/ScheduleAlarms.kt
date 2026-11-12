/*
 * ScheduleAlarms.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val LOG = LoggerFactory.logger {  }

/**
 * Drives [Schedules] in the app: a thread of its own looks when the next task is due, and at least once a minute, as
 * the wall clock may change. A timer alone stalls while the device sleeps, so an alarm is set for the next task too:
 * one that needs no permission, which Android may deliver late in Doze, as it batches those.
 */
internal class ScheduleAlarms private constructor(context: Context) : Schedules.Driver {

    private val context = context.applicationContext

    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "schedules").apply { isDaemon = true }
    }

    private var check: ScheduledFuture<*>? = null

    /** The alarm set last, in milliseconds since 1970; null when none is. */
    private var alarmAt: Long? = null

    override fun poke() {
        executor.execute(::tick)
    }

    /** On the driver's thread only. */
    private fun tick() {
        val now = Schedules.clock()
        val earliest = try {
            Schedules.runDue(now)
        } catch (e: Exception) {
            LOG.error { "Couldn't run the scheduled tasks: $e" }
            now + LOOK_AGAIN_MILLIS
        }
        check?.cancel(false)
        check = if (earliest == null) null else
            executor.schedule(::tick, (earliest - now).coerceIn(0, LOOK_AGAIN_MILLIS), TimeUnit.MILLISECONDS)
        setAlarm(earliest)
    }

    private fun setAlarm(at: Long?) {
        if (at == alarmAt)
            return
        val manager = context.getSystemService(AlarmManager::class.java)
            ?: return
        try {
            if (at == null)
                manager.cancel(pendingIntent())
            else
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent())
            alarmAt = at
        } catch (e: Exception) {
            LOG.error { "Couldn't set the alarm for scheduled tasks: $e" }
        }
    }

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(context, REQUEST_CODE, Intent(context, Receiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Woken by the alarm: keeps the device awake a moment, for the tasks due to start. */
    private fun onAlarm() {
        val power = context.getSystemService(PowerManager::class.java)
        power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StarLight:schedules")
            ?.acquire(WAKE_MILLIS)
        executor.execute {
            // Gone off: the next one is set again, even for the same time.
            alarmAt = null
            tick()
        }
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // A process started by the alarm has no tasks until its projects compile; they set the alarm again.
            instance?.onAlarm()
        }
    }

    companion object {
        private const val REQUEST_CODE = 0x5C4ED
        private const val LOOK_AGAIN_MILLIS = 60_000L
        private const val WAKE_MILLIS = 10_000L

        @Volatile
        private var instance: ScheduleAlarms? = null

        /** Starts driving [Schedules]; once, as the app starts. */
        fun init(context: Context) {
            if (instance != null)
                return
            instance = ScheduleAlarms(context).also { Schedules.driver = it }
        }
    }
}
