/*
 * ProjectEvent.kt created by Minki Moon(mooner1022) on 22. 2. 2. 오후 5:53
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.event

import kotlin.reflect.KClass

abstract class ProjectEvent {

    abstract val id: String

    abstract val name: String

    abstract val functionName: String

    abstract val argTypes: Array<KClass<*>>

    override fun equals(other: Any?): Boolean = other is ProjectEvent && other.id == id

    override fun hashCode(): Int = name.hashCode() + id.hashCode()
}