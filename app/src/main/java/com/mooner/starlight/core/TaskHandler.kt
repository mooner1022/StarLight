package com.mooner.starlight.core

import com.mooner.starlight.core.ApplicationSession.projectLoader

class TaskHandler {
    fun callEvent(listenerName: String, eventName: String, args: Array<Any>) {
        val projects = projectLoader.getProjects()
        for (project in projects) {
            if (project.config.listeners.contains(listenerName)) {
                project.callEvent(eventName, args)
            }
        }
    }
}