/*
 * BroadcastApi.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

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
            BroadcastManager.unregisterAll(project)
    }

    object BroadcastManager {

        private val callbacks: ConcurrentHashMap<ProjectID, ProjectCallbacks> =
            ConcurrentHashMap()

        fun send(name: String, value: Any) {
            for ((_, projCallback) in callbacks) {
                projCallback[name]
                    ?.forEach { v -> v.invoke(value) }
            }
        }

        fun register(project: Project, name: String, task: BroadcastCallback) {
            callbacks.computeIfAbsent(project.info.id) { id ->
                project.getLifecycle().registerObserver(ReceiverObserver(id))
                ConcurrentHashMap()
            }.getOrPut(name, ::CopyOnWriteArraySet) += task
        }

        fun unregister(project: Project, name: String, task: BroadcastCallback) {
            callbacks[project.info.id]
                ?.get(name)
                ?.remove(task)
        }

        fun unregisterAll(project: Project) {
            callbacks[project.info.id]?.clear()
        }

        /** The receivers belong to the code that registered them: they go when it's compiled again or destroyed. */
        private class ReceiverObserver(
            private val id: ProjectID
        ): ProjectLifecycleObserver.ExplicitObserver {

            override fun onCompileStart(project: Project) = remove(project)

            override fun onDestroy(project: Project) = remove(project)

            private fun remove(project: Project) {
                callbacks -= id
                project.getLifecycle().unregisterObserver(this)
            }
        }
    }
}
