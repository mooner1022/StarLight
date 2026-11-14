/*
 * BroadcastApi.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.api.msgbot.scriptFunctionIdentity
import dev.mooner.starlight.languages.neonjs.whileWaitingForAnotherThread
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val LOG = LoggerFactory.logger {  }

private typealias BroadcastCallback = (value: Any?) -> Unit

private fun requireFunction(function: BroadcastCallback?): BroadcastCallback =
    function ?: throw IllegalArgumentException("function must be a function")

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
     * MessengerBot R's `Broadcast`: passes values between projects by name; a global in legacy and API2 projects.
     *
     * A project registers receivers for a name, and `send` calls the receivers of that name in every project, while
     * `sendLocal` calls only this project's. Receivers stay until they are unregistered, or until the project compiles
     * again or is unloaded. Event names are text: a number or boolean is turned into its text (`1` is `"1"`), and
     * `null` or `undefined` throws `"event must be a string"`.
     *
     * Receivers run right away on the thread that called `send`, so a receiver that waits for another project's
     * script can hang both.
     */
    class Broadcast(
        private val project: Project
    ) {

        /**
         * Calls every receiver registered for [event], in every project this one included, with [value].
         *
         * The receivers run right away, one after another, on the sending script's thread rather than on their
         * projects' workers, in the order they registered within a project. Projects that aren't compiled are skipped.
         * An error thrown by a receiver is only logged: it doesn't reach the caller of `send`, and the receivers after
         * it still run. Nothing happens when no receiver has the name. It returns nothing.
         *
         * @param event The name receivers registered for.
         * @param value The value to pass, as it is and not copied; `null` when left out.
         * @throws IllegalArgumentException `"event must be a string"` when [event] is `null` or `undefined`.
         */
        @JvmOverloads
        fun send(event: Any?, value: Any? = null) =
            BroadcastManager.send(project, ScriptArgs.identifier(event, "event"), ScriptArgs.handOn(value))

        /**
         * Calls the receivers this project registered for [event], with [value].
         *
         * It runs them as `send` does, but only this project's. Nothing happens when this project has none for the
         * name.
         *
         * @param event The name receivers registered for.
         * @param value The value to pass, as it is and not copied; `null` when left out.
         * @throws IllegalArgumentException `"event must be a string"` when [event] is `null` or `undefined`.
         */
        @JvmOverloads
        fun sendLocal(event: Any?, value: Any? = null) =
            BroadcastManager.sendLocal(project, ScriptArgs.identifier(event, "event"), ScriptArgs.handOn(value))

        /**
         * Registers [function] as this project's receiver for [event].
         *
         * It stays until it is unregistered, or until this project compiles again or is unloaded. Registering the same
         * function twice makes it run twice for each value sent.
         *
         * @param event The name to receive.
         * @param function The function called with the value sent under the name.
         * @throws IllegalArgumentException `"function must be a function"` when [function] isn't one;
         * `"event must be a string"` when [event] is `null` or `undefined`.
         */
        fun register(event: Any?, function: BroadcastCallback?) {
            val task = requireFunction(function)
            BroadcastManager.register(project, ScriptArgs.identifier(event, "event"), task)
        }

        /**
         * Removes every registration of [function] from this project's receivers for [event].
         *
         * The function is matched as the script function itself, in Rhino and NeonJS alike, so pass the same function
         * that was registered; a new function written out again doesn't match. Nothing happens when nothing matches.
         *
         * @param event The name it was registered for.
         * @param function The function that was registered.
         * @throws IllegalArgumentException `"function must be a function"` when [function] isn't one;
         * `"event must be a string"` when [event] is `null` or `undefined`.
         */
        fun unregister(event: Any?, function: BroadcastCallback?) {
            val task = requireFunction(function)
            BroadcastManager.unregister(project, ScriptArgs.identifier(event, "event"), task)
        }

        /** Removes every receiver this project registered. */
        fun unregisterAll() =
            BroadcastManager.unregisterAll(project)

        /**
         * The names that have receivers in any project, each once, in no particular order.
         *
         * A name whose receivers were all unregistered one by one is still listed, until `unregisterAll` or a new
         * compile of its project drops it.
         *
         * @return A Java `String` array.
         */
        fun getAllEvents(): Array<String> =
            BroadcastManager.allEvents()

        /**
         * The names this project registered receivers for, as [getAllEvents] lists them but for this project only.
         *
         * @return A Java `String` array; empty when there are none.
         */
        fun getLocalEvents(): Array<String> =
            BroadcastManager.localEvents(project)

        /**
         * How many receivers are registered for [event] in all projects together, one for each registration.
         *
         * @param event The name to count.
         * @throws IllegalArgumentException `"event must be a string"` when [event] is `null` or `undefined`.
         */
        fun getListenerCount(event: Any?): Int =
            BroadcastManager.count(null, ScriptArgs.identifier(event, "event"))

        /**
         * How many receivers this project registered for [event].
         *
         * @param event The name to count.
         * @throws IllegalArgumentException `"event must be a string"` when [event] is `null` or `undefined`.
         */
        fun getLocalListenerCount(event: Any?): Int =
            BroadcastManager.count(project, ScriptArgs.identifier(event, "event"))

        /**
         * Whether any project has a receiver registered for [event].
         *
         * @param event The name to look for.
         * @throws IllegalArgumentException `"event must be a string"` when [event] is `null` or `undefined`.
         */
        fun hasListeners(event: Any?): Boolean =
            getListenerCount(event) > 0

        /**
         * Whether this project has a receiver registered for [event].
         *
         * @param event The name to look for.
         * @throws IllegalArgumentException `"event must be a string"` when [event] is `null` or `undefined`.
         */
        fun hasLocalListeners(event: Any?): Boolean =
            getLocalListenerCount(event) > 0

        /** This project's name, as the other APIs call a script's name. */
        fun getScriptName(): String =
            project.info.name
    }

    /** One registration: the function, and the script function behind it, which unregistering compares. */
    private class Receiver(val callback: BroadcastCallback, val identity: Any)

    /** A project's registrations by event name. A name stays once added, even with no receiver left. */
    private class Receivers(val project: Project) {
        val events: ConcurrentHashMap<String, CopyOnWriteArrayList<Receiver>> = ConcurrentHashMap()
    }

    internal object BroadcastManager {

        private val callbacks: ConcurrentHashMap<ProjectID, Receivers> =
            ConcurrentHashMap()

        // Receivers run here, on the sending thread. Another project's receivers run while the sender's own NeonJS
        // context is released: two NeonJS projects broadcasting to each other would otherwise each hold their own
        // context and wait for the other's, which hangs both, as NeonContext waits without a limit. The sender's own
        // receivers stay in its context.
        fun send(sender: Project, name: String, value: Any?) {
            for (receivers in callbacks.values) {
                val project = receivers.project
                // A project that is compiling is running the code that registers; only a stopped one is skipped.
                if (!project.isCompiled && project.getLifecycle().state != ProjectLifecycle.State.COMPILING)
                    continue
                if (project === sender)
                    deliver(receivers, name, value)
                else
                    whileWaitingForAnotherThread { deliver(receivers, name, value) }
            }
        }

        fun sendLocal(project: Project, name: String, value: Any?) {
            callbacks[project.info.id]?.let { deliver(it, name, value) }
        }

        private fun deliver(receivers: Receivers, name: String, value: Any?) {
            // The list is a snapshot while it is walked: a receiver may register or unregister meanwhile.
            receivers.events[name]?.forEach { receiver ->
                try {
                    receiver.callback(value)
                } catch (e: Exception) {
                    LOG.error { "Broadcast: receiver of '$name' in '${receivers.project.info.name}' failed: $e" }
                }
            }
        }

        fun register(project: Project, name: String, task: BroadcastCallback) {
            val receivers = callbacks.computeIfAbsent(project.info.id) { id ->
                project.getLifecycle().registerObserver(ReceiverObserver(id))
                Receivers(project)
            }
            receivers.events.computeIfAbsent(name) { CopyOnWriteArrayList() } += Receiver(task, scriptFunctionIdentity(task))
        }

        fun unregister(project: Project, name: String, task: BroadcastCallback) {
            val identity = scriptFunctionIdentity(task)
            callbacks[project.info.id]
                ?.events?.get(name)
                ?.removeAll { it.identity == identity }
        }

        fun unregisterAll(project: Project) {
            callbacks[project.info.id]?.events?.clear()
        }

        fun allEvents(): Array<String> =
            callbacks.values.flatMapTo(LinkedHashSet()) { it.events.keys }.toTypedArray()

        fun localEvents(project: Project): Array<String> =
            callbacks[project.info.id]?.events?.keys?.toTypedArray() ?: emptyArray()

        /** The receivers for [name] in [project], or in every project when it is `null`. */
        fun count(project: Project?, name: String): Int =
            if (project != null)
                callbacks[project.info.id]?.events?.get(name)?.size ?: 0
            else
                callbacks.values.sumOf { it.events[name]?.size ?: 0 }

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
