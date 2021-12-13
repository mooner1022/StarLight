package com.mooner.starlight.plugincore.event

import com.mooner.starlight.plugincore.logger.Logger

class EventManager {

    private val mEvents: MutableSet<Event> = hashSetOf()
    val events: Set<Event> get() = mEvents

    fun addEvent(event: Event) {
        mEvents += event
    }

    fun hasEvent(id: String): Boolean = events.any { it.id == id }

    inline fun <reified T: Event> EventManager.hasEvent(): Boolean = events.any { T::class.isInstance(it) }

    internal fun purge() {
        mEvents.clear()
    }
}

public inline fun <reified T: Event> EventManager.callEvent(args: Array<out Any>, noinline onError: (e: Throwable) -> Unit = {}): Boolean {
    Logger.v("EventManager", "Calling event ${T::class.simpleName} with args $args")
    val event = events.find { T::class.isInstance(it) }?: return false
    event.callEvent(args, onError)
    return true
}