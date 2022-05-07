/*
 * ProjectUtils.kt created by Minki Moon(mooner1022) on 22. 2. 3. 오전 3:01
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.full.createInstance

inline fun <reified T: ProjectEvent> Project.fireEvent(vararg args: Any, noinline onFailure: (e: Throwable) -> Unit = {}): Boolean {
    Logger.v("EventManager", "Calling explicit event ${T::class.simpleName} for project ${this.info.name}")
    val event = T::class.createInstance().also { event ->
        for ((index, arg) in event.argTypes.withIndex()) {
            if (arg != args[index]::class)
                error("Passed argument types [${args.joinToString { clazz -> clazz::class.simpleName.toString() }}] do not match the required argument types: [${event.argTypes.joinToString { clazz -> clazz.simpleName.toString() }}]")
        }
    }
    this.callFunction(event.functionName, args, onFailure)
    return true
}