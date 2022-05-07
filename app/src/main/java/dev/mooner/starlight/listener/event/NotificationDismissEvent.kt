package dev.mooner.starlight.listener.event

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.eventCoroutineScope
import kotlinx.coroutines.CoroutineScope

data class NotificationDismissEvent(
    val sbn: StatusBarNotification,
    val rankingMap: NotificationListenerService.RankingMap,
    val reason: Int,
    val coroutineScope: CoroutineScope = eventCoroutineScope
): Event, CoroutineScope by coroutineScope