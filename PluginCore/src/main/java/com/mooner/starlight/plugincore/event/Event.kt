package com.mooner.starlight.plugincore.event

import com.mooner.starlight.plugincore.Session

abstract class Event {

    abstract val id: String

    abstract val name: String

    abstract val eventName: String

    open val compatibleLanguageId: List<String> = listOf()

    fun callEvent(args: Array<out Any>, onException: (e: Throwable) -> Unit) = Session.projectManager.callEvent(id, eventName, args, onException)

    override fun equals(other: Any?): Boolean = other is Event && other.id == id

    override fun hashCode(): Int = name.hashCode() + id.hashCode()
}