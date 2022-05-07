package dev.mooner.starlight.listener.event

import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.eventCoroutineScope
import kotlinx.coroutines.CoroutineScope

data class NotificationPostEvent(
    val statusBarNotification: StatusBarNotification,
    val coroutineScope: CoroutineScope = eventCoroutineScope
): Event, CoroutineScope by coroutineScope