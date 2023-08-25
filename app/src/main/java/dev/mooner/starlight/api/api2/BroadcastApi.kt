/*
 * BroadcastApi.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.project.Project

private typealias BroadcastCallback = (value: Any) -> Unit
private typealias ProjectCallbacks = MutableMap<String, MutableSet<BroadcastCallback>>

class BroadcastApi: Api<BroadcastApi.Broadcast>() {

    override val name: String = "Broadcast"

    override val objects: List<ApiObject> =
        getApiObjects<Broadcast>()

    override val instanceClass: Class<Broadcast> =
        Broadcast::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any {
        return Broadcast(project)
    }

    class Broadcast(
        private val project: Project
    ) {

        fun send(broadcastName: String, value: Any) =
            BroadcastManager.send(broadcastName, value)

        fun register(broadcastName: String, task: BroadcastCallback) =
            BroadcastManager.register(project, broadcastName, task)

        fun unregister(broadcastName: String, task: BroadcastCallback) =
            BroadcastManager.unregister(project, broadcastName, task)

        fun unregisterAll() =
            BroadcastManager.unregisterAll()
    }

    object BroadcastManager {

        private val callbacks: MutableMap<String, ProjectCallbacks> =
            hashMapOf()

        fun send(name: String, value: Any) {
            for ((_, projCallback) in callbacks) {
                if (name in projCallback)
                    projCallback[name]
                        ?.forEach { v -> v.invoke(value) }
            }
        }

        fun register(project: Project, name: String, task: BroadcastCallback) {
            val key = project.info.name
            callbacks.putIfAbsent(key, hashMapOf())

            callbacks[key]!!.let { projCallback ->
                projCallback.putIfAbsent(name, hashSetOf())
                projCallback[name]!! += task
            }
        }

        fun unregister(project: Project, name: String, task: BroadcastCallback) {
            val key = project.info.name
            if (key !in callbacks)
                return

            callbacks[key]!!.let { projCallback ->
                if (name !in projCallback)
                    return

                projCallback[name]!! -= task
            }
        }

        fun unregisterAll() {
            callbacks.clear()
        }

        private fun removeProjectCallback(projectName: String) {
            if (projectName in callbacks) {
                callbacks[projectName]!!.clear()
                callbacks -= projectName
            }
        }

        private fun onProjectCompile(event: Events.Project.Compile) {
            removeProjectCallback(event.project.info.name)
        }

        private fun onProjectDelete(event: Events.Project.Delete) {
            removeProjectCallback(event.projectName)
        }

        init {
            EventHandler.apply {
                on(this, ::onProjectCompile)
                on(this, ::onProjectDelete)
            }
        }
    }
}