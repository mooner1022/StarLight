/*
 * ProjectUtils.kt created by Minki Moon(mooner1022) on 22. 1. 8. 오후 7:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.logger.Logger

public inline fun <reified T: Event> Project.callEvent(args: Array<out Any>, noinline onException: (e: Throwable) -> Unit = {}): Boolean {
    Logger.v("EventManager", "Calling explicit event ${T::class.simpleName} for project ${this.info.name}")
    val event = dev.mooner.starlight.plugincore.Session.eventManager.events.find { T::class.isInstance(it) }?: return false
    this.callEvent(event.eventName, args, onException)
    return true
}