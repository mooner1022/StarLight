package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.event.Event
import com.mooner.starlight.plugincore.logger.Logger

public inline fun <reified T: Event> Project.callEvent(args: Array<out Any>, noinline onException: (e: Throwable) -> Unit = {}): Boolean {
    Logger.v("EventManager", "Calling explicit event ${T::class.simpleName} for project ${this.info.name}")
    val event = Session.eventManager.events.find { T::class.isInstance(it) }?: return false
    this.callEvent(event.eventName, args, onException)
    return true
}