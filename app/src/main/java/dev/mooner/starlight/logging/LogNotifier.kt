/*
 * LogNotifier.kt created by Minki Moon(mooner1022) on 1/18/23, 3:22 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.logging

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.api.original.NotificationApi
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogData.Companion.FLAG_NOTIFY
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.utils.hasFlag
import dev.mooner.starlight.utils.PeekLevel
import dev.mooner.starlight.utils.createSimplePeek
import dev.mooner.starlight.utils.isForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias LogFilter = (log: LogData) -> Boolean

context(lifecycle: LifecycleOwner)
fun Activity.bindLogNotifier(filter: LogFilter? = null) =
    lifecycle.lifecycleScope.launch {
        suspend fun onLogCreated(event: Events.Log.Create) {
            val log = event.log
            if (log.type.priority >= LogType.INFO.priority) {
                if (log.type == LogType.INFO && !log.flags.hasFlag(FLAG_NOTIFY))
                    return

                if (filter != null && !filter(log))
                    return

                withContext(Dispatchers.Main) {
                    if (log.type == LogType.INFO && GlobalApplication.state != GlobalApplication.STATE_FOREGROUND) {
                        buildNotification(10) {
                            setTitle(log.tag)
                            setText(log.message)
                        }.build()
                        return@withContext
                    }
                    createLogPeek(log).peek()
                }
            }
        }

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            EventHandler.on(this, ::onLogCreated)
        }
    }

context(lifecycle: LifecycleOwner)
fun Fragment.bindLogNotifier(filter: LogFilter? = null) =
    lifecycle.lifecycleScope.launch {
        suspend fun onLogCreated(event: Events.Log.Create) {
            val log = event.log
            if (log.type.priority >= LogType.INFO.priority) {
                if (log.type == LogType.INFO && !log.flags.hasFlag(FLAG_NOTIFY))
                    return

                if (filter != null && !filter(log))
                    return

                withContext(Dispatchers.Main) {
                    if (log.type == LogType.INFO && !isForeground()) {
                        requireContext().buildNotification(10) {
                            setTitle(log.tag ?: log.type.toString())
                            setText(log.message)
                        }.build()
                        return@withContext
                    }
                    (activity ?: return@withContext).createLogPeek(log).peek()
                }
            }
        }

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            EventHandler.on(this, ::onLogCreated)
        }
    }

/**
 * The alert for a log. A warning or an error has its level's look. What a script asked to be told to the user is
 * the script speaking, and has the gold: it is the one kind of alert that isn't the app's own.
 */
private fun Activity.createLogPeek(log: LogData): PeekAlert {
    val level = when (log.type) {
        LogType.WARN                    -> PeekLevel.WARNING
        LogType.ERROR, LogType.CRITICAL -> PeekLevel.ERROR
        else                            -> PeekLevel.INFO
    }
    return createSimplePeek(title = log.tag ?: log.type.toString(), text = log.message, level = level) {
        autoHideMillis = LOG_PEEK_MILLIS
        if (level == PeekLevel.INFO) {
            iconRes = dev.mooner.configdsl.R.drawable.ic_round_mark_chat_unread_24
            iconTint(res = R.color.peek_accent)
        }
    }
}

private const val LOG_PEEK_MILLIS = 5000L

private fun Context.buildNotification(id: Int, block: NotificationApi.NotificationBuilder.() -> Unit): NotificationCompat.Builder {
    createNotificationChannel("LogNotifier", "로그 알림 채널")
    val builder = NotificationApi.NotificationBuilder(id).apply(block)
    return NotificationCompat.Builder(this, "LogNotifier").apply {
        setContentTitle(builder.mTitle)
        setSubText(builder.mText)
        setSmallIcon(R.drawable.ic_notification)
        color = ContextCompat.getColor(this@buildNotification, R.color.main_bright)
        if (builder.lightArgb != null)
            setLights(builder.lightArgb!!, builder.lightOnMs, builder.lightOffMs)
        setShowWhen(false)
    }
}

private fun Context.createNotificationChannel(channelId: String, channelName: String) {
    val notificationChannel = NotificationChannel(
        channelId,
        channelName,
        NotificationManager.IMPORTANCE_DEFAULT
    )

    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(notificationChannel)
}