package com.mooner.starlight.core

import com.mooner.starlight.core.ApplicationSession.projectLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskHandler {
    fun fireEvent(listenerName: String, eventName: String, args: Array<Any>) {
        val projects = projectLoader.getProjects()
        for (project in projects.filter { it.config.isEnabled }) {
            CoroutineScope(Dispatchers.Main).launch {
                project.callEvent(eventName, args)
            }
        }
    }

    fun bindRepliers(listener: (room: String, msg: String) -> Unit) {
        val projects = projectLoader.getProjects()
        for (project in projects) {
            project.bindReplier(listener)
        }
    }
}