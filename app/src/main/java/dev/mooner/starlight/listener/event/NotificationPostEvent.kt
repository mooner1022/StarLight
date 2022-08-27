package dev.mooner.starlight.listener.event

import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.eventHandlerScope
import kotlinx.coroutines.CoroutineScope

data class NotificationPostEvent(
    val statusBarNotification: StatusBarNotification,
    val coroutineScope: CoroutineScope = eventHandlerScope()
): Event, CoroutineScope by coroutineScope