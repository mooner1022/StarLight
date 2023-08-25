/*
 * BotManagerApi.kt created by Minki Moon(mooner1022) on 8/19/23, 3:42 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.starlight.api.node.EventCallback
import dev.mooner.starlight.api.node.EventEmitterApi
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

private typealias ListenerCallback = (param: Any) -> Unit

class BotManagerApi: Api<BotManagerApi.BotManager>() {

    override val name: String = "BotManager"

    override val objects: List<ApiObject> =
        getApiObjects<BotManager>()

    override val instanceClass: Class<BotManager> =
        BotManager::class.java

    override val instanceType: InstanceType =
        InstanceType.CLASS

    override fun getInstance(project: Project): Any =
        BotManager(project)

    class BotManager(
        private val project: Project
    ) {

        private val _currentBot: Bot by lazy { Bot.fromProject(project) }

        fun getCurrentBot(): Bot {
            return _currentBot
        }

        fun getBot(botName: String): Bot? =
            Session.projectManager
                .getProject(botName)
                ?.let(Bot::fromProject)

        @JvmOverloads
        fun getRooms(packageName: String? = null): Array<String> =
            NotificationListener.getRoomNames().toTypedArray()
    }

    class Bot private constructor(
        private val project: Project
    ) {
        private val listenerMapping: MutableMap<ListenerCallback, EventCallback> by lazy(::hashMapOf)
        private val emitter: EventEmitterApi.EventEmitter by lazy(EventEmitterApi::EventEmitter)

        fun setCommandPrefix(prefix: String) {

        }

        @JvmOverloads
        fun send(room: String, message: String, packageName: String? = null): Boolean =
            NotificationListener.sendTo(room, message)

        @JvmOverloads
        fun canReply(room: String, packageName: String? = null): Boolean =
            NotificationListener.hasRoom(room)

        @JvmOverloads
        fun markAsRead(room: String?, packageName: String? = null): Boolean {
            return if (room == null)
                NotificationListener.markAsRead()
            else
                NotificationListener.markAsRead(room)
        }

        fun getName(): String =
            project.info.name

        fun setPower(power: Boolean) =
            project.setEnabled(power)

        fun getPower(): Boolean =
            project.info.isEnabled

        fun compile() =
            project.compile(throwException = true)

        fun unload() =
            project.destroy(requestUpdate = true)

        fun on(eventName: String, listener: ListenerCallback) {
            emitter.on(eventName, wrapListener(listener))
        }

        fun addListener(eventName: String, listener: ListenerCallback) =
            on(eventName, listener)

        @JvmOverloads
        fun off(eventName: String, listener: ListenerCallback? = null) {
            if (listener == null)
                emitter.off(eventName, null)
            else
                emitter.off(eventName, listenerMapping[listener])
        }

        @JvmOverloads
        fun removeListener(eventName: String, listener: ListenerCallback? = null) =
            off(eventName, listener)

        fun removeAllListeners(eventName: String) =
            emitter.removeAllListeners(eventName)

        fun prependListener(eventName: String, listener: ListenerCallback) =
            emitter.prependListener(eventName, wrapListener(listener))

        fun listeners(eventName: String): List<ListenerCallback> =
            emitter.listeners(eventName)
                .mapNotNull { ec -> getListenerByEvent(ec) }

        private fun wrapListener(listener: ListenerCallback): EventCallback =
            ({ v: Array<out Any> -> listener(v[0]) })
                .also { listenerMapping[listener] = it }

        private fun getListenerByEvent(event: EventCallback): ListenerCallback? {
            for ((k, v) in listenerMapping) {
                if (event == v)
                    return k
            }
            return null
        }

        init {
            emitter.on("removeListener") { args ->
                val listener = args[0]
                var key: ListenerCallback? = null
                for ((k, v) in listenerMapping)
                    if (v == listener) {
                        key = k
                        break
                    }
                key?.let(listenerMapping::remove)
            }
        }

        companion object {
            fun fromProject(project: Project): Bot =
                Bot(project)
        }
    }
}