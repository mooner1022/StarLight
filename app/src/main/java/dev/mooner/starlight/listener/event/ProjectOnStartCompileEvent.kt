/*
 * ProjectOnStartCompileEvent.kt created by Minki Moon(mooner1022) on 7/26/23, 7:54 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class ProjectOnStartCompileEvent: ProjectEvent() {

    override val id: String = "compile"

    override val name: String = "onStartCompile 이벤트"

    override val functionName: String = "onStartCompile"

    override val argTypes: Array<KClass<*>> = emptyArray()
}