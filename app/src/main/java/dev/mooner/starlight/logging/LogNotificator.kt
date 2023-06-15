/*
 * LogNotificator.kt created by Minki Moon(mooner1022) on 1/18/23, 3:22 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.logging

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.ViewGroup.LayoutParams
import androidx.annotation.FontRes
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mooner.peekalert.PeekAlert
import dev.mooner.peekalert.createPeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.api.original.NotificationApi
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogData.Companion.FLAG_NOTIFY
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.utils.hasFlag
import dev.mooner.starlight.utils.dp
import dev.mooner.starlight.utils.isForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var typefaceCache: MutableMap<Int, Typeface> = hashMapOf()
private fun getTypeface(context: Context, @FontRes res: Int): Typeface? =
    typefaceCache[res]
        ?: context
            .let { ResourcesCompat.getFont(it, res) }
            ?.also { typefaceCache[res] = it }

context(LifecycleOwner)
fun Activity.bindLogNotifier() =
    lifecycleScope.launch {
        suspend fun onLogCreated(event: Events.Log.Create) {
            val log = event.log
            if (log.type.priority >= LogType.INFO.priority) {
                if (log.type == LogType.INFO && !log.flags.hasFlag(FLAG_NOTIFY))
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
                                    title = log.tag
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
fun Fragment.bindLogNotifier() =
    lifecycleScope.launch {
        suspend fun onLogCreated(event: Events.Log.Create) {
            val log = event.log
            if (log.type.priority >= LogType.WARN.priority) {
                val peekAlert = createPeek(log.tag ?: "null", log.message)
                withContext(Dispatchers.Main) {
                    when(log.type) {
                        LogType.WARN ->
                            peekAlert.setBackgroundColor(res = R.color.noctis_orange)
                        LogType.ERROR, LogType.CRITICAL ->
                            peekAlert.setBackgroundColor(res = R.color.code_error)
                        else ->
                            peekAlert
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
        autoHideMillis = 5000L
        paddingDp = 16
        position = PeekAlert.Position.Top
        width = LayoutParams.WRAP_CONTENT
        cornerRadius = dp(14).toFloat()
        iconRes = R.drawable.ic_round_info_24
        iconTint(res = R.color.white)
        draggable = true
        title(title) {
            textColor(R.color.white)
            textSize = 14f
            typeface = getTypeface(this@createPeek.requireContext(), R.font.nanumsquare_round_extrabold)
        }
        text(text) {
            textColor(R.color.white)
            textSize = 12f
            typeface = getTypeface(this@createPeek.requireContext(), R.font.nanumsquare_round_regular)
        }
    }
}

private fun Activity.createPeek(title: String, text: String): PeekAlert {
    return createPeekAlert(this) {
        autoHideMillis = 5000L
        paddingDp = 16
        position = PeekAlert.Position.Top
        width = LayoutParams.WRAP_CONTENT
        cornerRadius = dp(14).toFloat()
        iconRes = R.drawable.ic_round_info_24
        iconTint(res = R.color.white)
        draggable = true
        title(title) {
            textColor(R.color.white)
            textSize = 14f
            typeface = getTypeface(this@createPeek, R.font.nanumsquare_round_extrabold)
        }
        text(text) {
            textColor(R.color.white)
            textSize = 12f
            typeface = getTypeface(this@createPeek, R.font.nanumsquare_round_regular)
        }
    }
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