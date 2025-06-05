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
import android.os.Build
import android.view.ViewGroup.LayoutParams
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mooner.peekalert.PeekAlert
import dev.mooner.peekalert.PeekAlertBuilder
import dev.mooner.peekalert.createPeekAlert
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
import dev.mooner.starlight.utils.dp
import dev.mooner.starlight.utils.getTypeface
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

                val peekAlert = createPeek(log.tag ?: "null", log.message)
                withContext(Dispatchers.Main) {
                    when(log.type) {
                        LogType.WARN ->
                            peekAlert.setBackgroundColor(res = R.color.noctis_orange)
                        LogType.ERROR, LogType.CRITICAL ->
                            peekAlert.setBackgroundColor(res = R.color.code_error)
                        else -> {
                            if (GlobalApplication.state == GlobalApplication.STATE_FOREGROUND)
                                peekAlert.setBackgroundColor(res = R.color.main_dark)
                            else {
                                buildNotification(10) {
                                    setTitle(log.tag)
                                    setText(log.message)
                                }.build()
                                return@withContext
                            }
                        }
                    }.peek()
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

                val peekAlert = createPeek(log.tag ?: "null", log.message)
                withContext(Dispatchers.Main) {
                    when(log.type) {
                        LogType.WARN ->
                            peekAlert.setBackgroundColor(res = R.color.noctis_orange)
                        LogType.ERROR, LogType.CRITICAL ->
                            peekAlert.setBackgroundColor(res = R.color.code_error)
                        else -> {
                            if (isForeground())
                                peekAlert.setBackgroundColor(res = R.color.main_dark)
                            else {
                                requireContext().buildNotification(10) {
                                    setTitle(log.tag ?: log.type.toString())
                                    setText(log.message)
                                }.build()
                                return@withContext
                            }
                        }
                    }.peek()
                }
            }
        }

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            EventHandler.on(this, ::onLogCreated)
        }
    }

private fun Fragment.createPeek(title: String, text: String): PeekAlert {
    return createPeekAlert(this) {
        setCommonAttrs()
        title(title) {
            textColor(R.color.white)
            textSize = 10f
            typeface = getTypeface(this@createPeek.requireContext(), R.font.nanumsquare_neo_regular)
        }
        text(text) {
            textColor(R.color.white)
            textSize = 12f
            typeface = getTypeface(this@createPeek.requireContext(), R.font.nanumsquare_neo_bold)
        }
    }
}

private fun Activity.createPeek(title: String, text: String): PeekAlert {
    return createPeekAlert(this) {
        setCommonAttrs()
        title(title) {
            textColor(R.color.white)
            textSize = 10f
            typeface = getTypeface(this@createPeek, R.font.nanumsquare_neo_regular)
        }
        text(text) {
            textColor(R.color.white)
            textSize = 12f
            typeface = getTypeface(this@createPeek, R.font.nanumsquare_neo_bold)
        }
    }
}

private fun PeekAlertBuilder.setCommonAttrs() {
    autoHideMillis = 5000L
    paddingDp = 12
    position = PeekAlert.Position.Top
    width = LayoutParams.WRAP_CONTENT
    cornerRadius = dp(14).toFloat()
    iconRes = dev.mooner.configdsl.R.drawable.ic_round_mark_chat_unread_24
    iconTint(res = R.color.white)
    draggable = true
}

private fun Context.buildNotification(id: Int, block: NotificationApi.NotificationBuilder.() -> Unit): NotificationCompat.Builder {
    createNotificationChannel("LogNotifier", "로그 알림 채널")
    val builder = NotificationApi.NotificationBuilder(id).apply(block)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationCompat.Builder(this, "LogNotifier")
    } else {
        NotificationCompat.Builder(this)
    }.apply {
        setContentTitle(builder.mTitle)
        setSubText(builder.mText)
        setSmallIcon(R.mipmap.ic_launcher)
        if (builder.lightArgb != null)
            setLights(builder.lightArgb!!, builder.lightOnMs, builder.lightOffMs)
        setShowWhen(false)
    }
}

private fun Context.createNotificationChannel(channelId: String, channelName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationChannel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(notificationChannel)
    }
}