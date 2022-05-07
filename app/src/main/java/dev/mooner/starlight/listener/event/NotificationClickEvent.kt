package dev.mooner.starlight.listener.event

import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.eventCoroutineScope
import kotlinx.coroutines.CoroutineScope

data class NotificationClickEvent(
    val notificationId: Int,
    val coroutineScope: CoroutineScope = eventCoroutineScope
): Event, CoroutineScope by coroutineScope