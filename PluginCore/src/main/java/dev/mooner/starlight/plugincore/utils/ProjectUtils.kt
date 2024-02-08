/*
 * ProjectUtils.kt created by Minki Moon(mooner1022) on 22. 2. 3. 오전 3:01
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance
import kotlin.reflect.full.isSubclassOf

inline fun <reified T: ProjectEvent> Project.fireEvent(vararg args: Any, noinline onFailure: (e: Throwable) -> Unit = {}): Boolean {
    logger.v("EventManager", "Calling explicit event ${T::class.simpleName} for project ${this.info.name}")

    val eventId = ProjectEventManager.validateAndGetEventID(T::class)
        ?: error("Unregistered event: ${T::class.qualifiedName}")

    if (!this.isEventCallAllowed(eventId))
        return false;

    val event = T::class.getInstance()
    val actualTypes = event.argTypes
        .map { it.type }
    if (actualTypes.size < args.size)
        error("Argument length mismatch, required: ${actualTypes.size}, provided: ${args.size}")

    for (i in args.indices) {
        val eArg = actualTypes[i]
        val pArg = args[i]::class
        if (!pArg.isSubclassOf(eArg))
            error("Argument type mismatch on position ${i}, required: ${eArg}, provided: $pArg")
    }

    this.callFunction(event.functionName, args, onFailure)
    return true
}