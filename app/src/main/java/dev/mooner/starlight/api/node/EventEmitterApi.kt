/*
 * EventEmitterApi.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.node

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project

internal typealias EventCallback = (Array<out Any>) -> Unit
private typealias CallbackMap = MutableList<CallbackData>

private val LOG = LoggerFactory.logger {  }

private data class CallbackData(
    val callback: EventCallback,
    val isOnce: Boolean = false
)

class EventEmitterApi: Api<EventEmitterApi.EventEmitter>() {

    override val name: String = "EventEmitter"

    override val objects: List<ApiObject> =
        getApiObjects<EventEmitter>()

    override val instanceClass: Class<EventEmitter> =
        EventEmitter::class.java

    override val instanceType: InstanceType =
        InstanceType.CLASS

    override fun getInstance(project: Project): Any =
        EventEmitter::class.java

    class EventEmitter {

        private val listeners: MutableMap<String, CallbackMap> = hashMapOf()
        private var maxListeners: Int = 0

        fun addListener(eventName: String, listener: EventCallback): EventEmitter =
            on(eventName, listener)

        fun emit(eventName: String, vararg args: Any) {
            if (eventName !in listeners)
                return

            val expired: MutableSet<CallbackData> = hashSetOf()
            for (data in listeners[eventName]!!) {
                data.callback(args)
                if (data.isOnce)
                    expired += data
            }
            for (data in expired)
                emit(EVENT_REMOVE_LISTENER, eventName, data)
            listeners[eventName]!!.removeAll(expired)
        }

        fun eventNames(): Array<String> =
            listeners.keys.toTypedArray()

        fun getMaxListeners(): Int =
            maxListeners

        fun listenerCount(eventName: String): Int =
            listeners[eventName]?.size ?: 0

        fun listeners(eventName: String): Array<EventCallback> =
            listeners[eventName]?.map(CallbackData::callback)?.toTypedArray() ?: arrayOf()

        fun on(eventName: String, listener: EventCallback): EventEmitter {
            listeners.putIfAbsent(eventName, arrayListOf())
            listeners[eventName]!!.checkLimit {
                it += wrap(listener)
            }
            emit(EVENT_NEW_LISTENER, eventName, listener)
            return this
        }

        fun off(eventName: String, listener: EventCallback?): EventEmitter {
            if (eventName !in listeners)
                return this

            if (listener == null)
                listeners[eventName]!!.removeLast()
            else
                listeners[eventName]!!
                    .removeIf { it.callback == listener }
                    .also { removed -> if (removed) emit(
                        EVENT_REMOVE_LISTENER, eventName, listener) }
            return this
        }

        fun once(eventName: String, listener: EventCallback): EventEmitter {
            listeners.putIfAbsent(eventName, arrayListOf())
            listeners[eventName]!!.checkLimit {
                it += wrap(listener, isOnce = true)
            }
            emit(EVENT_NEW_LISTENER, eventName, listener)
            return this
        }

        fun prependListener(eventName: String, listener: EventCallback) {
            if (eventName !in listeners)
                listeners[eventName] = arrayListOf(wrap(listener))
            else {
                listeners[eventName]!!.checkLimit {
                    it.add(0, wrap(listener))
                }
            }
            emit(EVENT_NEW_LISTENER, eventName, listener)
        }

        fun prependOnceListener(eventName: String, listener: EventCallback) {
            if (eventName !in listeners)
                listeners[eventName] = arrayListOf(wrap(listener))
            else {
                listeners[eventName]!!.checkLimit {
                    it.add(0, wrap(listener, isOnce = true))
                }
            }
            emit(EVENT_NEW_LISTENER, eventName, listener)
        }

        @JvmOverloads
        fun removeAllListeners(eventName: String? = null) {
            if (eventName == null) {
                listeners.forEach { (k, v) -> emit(
                    EVENT_REMOVE_LISTENER, k, v) }
                listeners.clear()
            }
            else {
                if (eventName !in listeners)
                    return
                listeners[eventName]!!.let { list ->
                    list.forEach { v -> emit(
                        EVENT_REMOVE_LISTENER, eventName, v) }
                    list.clear()
                }
            }
        }

        fun removeListener(eventName: String, listener: EventCallback?) =
            off(eventName, listener)

        fun setMaxListeners(n: Int) {
            require(n >= 0) { "Max listener size must be n >= 0" }
            maxListeners = n
        }

        fun setMaxListeners(n: Double) {
            maxListeners = if (n.isInfinite())
                0
            else
                n.toInt()
        }

        fun rawListeners(eventName: String): Array<EventCallback> =
            listeners(eventName)

        private fun wrap(callback: EventCallback, isOnce: Boolean = false) =
            CallbackData(callback, isOnce)

        private fun CallbackMap.checkLimit(block: (callbacks: CallbackMap) -> Unit) {
            if (maxListeners != 0 && size >= maxListeners)
                LOG.warn { "Listener count exceeds maxListener($maxListeners): $size" }

            block(this)
        }

        companion object {
            private const val EVENT_NEW_LISTENER    = "newListener"
            private const val EVENT_REMOVE_LISTENER = "removeListener"
        }
    }
}