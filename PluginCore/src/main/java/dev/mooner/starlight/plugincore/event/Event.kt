/*
 * Event.kt created by Minki Moon(mooner1022) on 22. 1. 8. 오후 7:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.event

abstract class Event {

    abstract val id: String

    abstract val name: String

    abstract val eventName: String

    open val compatibleLanguageId: List<String> = listOf()

    fun callEvent(args: Array<out Any>, onException: (e: Throwable) -> Unit) = dev.mooner.starlight.plugincore.Session.projectManager.callEvent(id, eventName, args, onException)

    override fun equals(other: Any?): Boolean = other is Event && other.id == id

    override fun hashCode(): Int = name.hashCode() + id.hashCode()
}