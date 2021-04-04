package com.mooner.starlight.core

import com.mooner.starlight.core.ApplicationSession.projectLoader

class TaskHandler {
    init {
        /*
        b3EflsEaRR { eventName, arguments ->
            when(eventName) {
                "callFunction" -> {
                    val listenerName = arguments["listenerName"] as String
                    val eName = arguments["eventName"] as String
                    @Suppress("UNCHECKED_CAST")
                    val args = arguments["args"] as Array<Any>
                    callEvent(listenerName, eName, args)
                }
            }
        }
        */
    }

    fun callEvent(listenerName: String, eventName: String, args: Array<Any>) {
        val projects = projectLoader.getProjects()
        for (project in projects) {
            if (project.config.listeners.contains(listenerName)) {
                project.callEvent(eventName, args)
            }
        }
    }
}