package com.mooner.starlight.plugincore.annotations

import com.mooner.starlight.plugincore.event.EventType

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class StarLightEventListener(
    val eventType: EventType
)