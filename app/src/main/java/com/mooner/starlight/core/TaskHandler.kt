package com.mooner.starlight.core

import com.mooner.starlight.plugincore.Session.Companion.getProjectLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskHandler {
    fun callFunction(eventName: String, args: Array<Any>) {
        val projects = getProjectLoader().getProjects()
        for (project in projects.filter { it.config.isEnabled }) {
            CoroutineScope(Dispatchers.Main).launch {
                project.callEvent(eventName, args)
            }
        }
    }
}