/*
 * LogNotificator.kt created by Minki Moon(mooner1022) on 1/18/23, 3:22 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.logging

import android.app.Activity
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

context(LifecycleOwner)
fun Activity.bindLogNotifier(filter: LogFilter? = null) =
    lifecycleScope.launch {
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

context(LifecycleOwner)
fun Fragment.bindLogNotifier(filter: LogFilter? = null) =
    lifecycleScope.launch {
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
            textSize = 14f
            typeface = getTypeface(this@createPeek.requireContext(), R.font.wantedsans_medium)
        }
        text(text) {
            textColor(R.color.white)
            textSize = 12f
            typeface = getTypeface(this@createPeek.requireContext(), R.font.wantedsans_regular)
        }
    }
}

private fun Activity.createPeek(title: String, text: String): PeekAlert {
    return createPeekAlert(this) {
        setCommonAttrs()
        title(title) {
            textColor(R.color.white)
            textSize = 14f
            typeface = getTypeface(this@createPeek, R.font.wantedsans_medium)
        }
        text(text) {
            textColor(R.color.white)
            textSize = 12f
            typeface = getTypeface(this@createPeek, R.font.wantedsans_regular)
        }
    }
}

private fun PeekAlertBuilder.setCommonAttrs() {
    autoHideMillis = 5000L
    paddingDp = 17
    position = PeekAlert.Position.Top
    width = LayoutParams.WRAP_CONTENT
    cornerRadius = dp(14).toFloat()
    iconRes = R.drawable.ic_round_projects_24
    iconTint(res = R.color.white)
    draggable = true
}

private fun Context.buildNotification(id: Int, block: NotificationApi.NotificationBuilder.() -> Unit): NotificationCompat.Builder {
    val builder = NotificationApi.NotificationBuilder(id).apply(block)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationCompat.Builder(this, "LogNotificator")
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