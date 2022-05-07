package dev.mooner.starlight.event

import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.eventCoroutineScope
import kotlinx.coroutines.CoroutineScope

data class SessionStageUpdateEvent(
    val value: String?,
    val coroutineScope: CoroutineScope = eventCoroutineScope
): Event, CoroutineScope by coroutineScope