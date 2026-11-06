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

    /**
     * MessengerBot R's `Broadcast`: passes values between projects by name; a global in API2 projects.
     *
     * A project registers receivers for a name, and `send` calls the receivers of that name in every project. Of
     * MessengerBot R's `Broadcast`, StarLight has only `send`, `register`, `unregister` and `unregisterAll`.
     */
    class Broadcast(
        private val project: Project
    ) {

        /**
         * Calls every receiver registered for [broadcastName], in every project this one included, with [value].
         *
         * The receivers run right away, one after another, on the sending script's thread rather than on their
         * projects' workers, and whether or not their projects are on. An error thrown by a receiver reaches the
         * caller of `send` and skips the receivers after it. Nothing happens when no receiver has the name.
         *
         * @param broadcastName The name receivers registered for.
         * @param value The value to pass; it can't be `null`. Send structured data as JSON text.
         */
        fun send(broadcastName: String, value: Any) =
            BroadcastManager.send(broadcastName, value)

        /**
         * Registers [task] as this project's receiver for [broadcastName].
         *
         * It stays until it is unregistered, or until this project compiles again or is unloaded.
         *
         * @param broadcastName The name to receive.
         * @param task The function called with each value sent under the name.
         */
        fun register(broadcastName: String, task: BroadcastCallback) =
            BroadcastManager.register(project, broadcastName, task)

        /**
         * Removes [task] from this project's receivers for [broadcastName].
         *
         * It is matched by the Java object the engine passed for the function. NeonJS passes a new one each time, so
         * there this doesn't find the receiver; use `unregisterAll()` instead.
         *
         * @param broadcastName The name it was registered for.
         * @param task The function that was registered.
         */
        fun unregister(broadcastName: String, task: BroadcastCallback) =
            BroadcastManager.unregister(project, broadcastName, task)

        /** Removes every receiver this project registered. */
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
