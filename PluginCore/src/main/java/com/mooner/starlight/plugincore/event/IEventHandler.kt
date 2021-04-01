package com.mooner.starlight.plugincore.event

interface IEventHandler {
    val name: String

    fun onEvent(callback: (eventName: String, args: Array<Any>) -> Unit)
}