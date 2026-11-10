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

    /**
     * An event emitter in the style of Node.js's `EventEmitter`: create one with `new EventEmitter()`, add listeners
     * with `on`, and call them with `emit`.
     *
     * Unlike in Node.js, a listener receives one argument, a Java array of the values given to `emit`:
     * ```js
     * const bus = new EventEmitter();
     * bus.on("greet", (args) => Log.i("Hello, " + args[0]));
     * bus.emit("greet", "world");
     * ```
     * Listeners run at once, one after another in the order they were added, on the thread that calls `emit`. Adding a
     * listener emits `newListener`, whose listeners get the event's name and the new listener. Each emitter is
     * separate; nothing else emits on it.
     *
     * @see guide:engines/neonjs#java-access
     */
    class EventEmitter {

        private val listeners: MutableMap<String, CallbackMap> = hashMapOf()
        private var maxListeners: Int = 0

        /**
         * Adds [listener] to be called each time [eventName] is emitted; the same as `on`.
         *
         * @param eventName The event's name.
         * @param listener Called with one argument, a Java array of the values given to `emit`.
         * @return This emitter, for chaining.
         */
        fun addListener(eventName: String, listener: EventCallback): EventEmitter =
            on(eventName, listener)

        /**
         * Calls the listeners of [eventName] with [args], in the order they were added, before returning.
         *
         * Each listener receives one argument: a Java array of [args]. Listeners added with `once` are removed
         * afterwards. Nothing happens when the event has no listeners. An error thrown by a listener skips the rest and
         * reaches the caller. Adding or removing listeners of the same event from inside one of them can throw
         * `ConcurrentModificationException`.
         *
         * @param eventName The event's name.
         * @param args The values to hand the listeners, any number of them.
         */
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

        /**
         * The names of the events that listeners were added for.
         *
         * A name stays after its listeners are removed, until `removeAllListeners()` is called without one.
         */
        fun eventNames(): Array<String> =
            listeners.keys.toTypedArray()

        /**
         * How many listeners an event may have before adding another logs a warning; `0`, the default, means no limit.
         *
         * The listener is added either way. Change it with `setMaxListeners(n)`.
         */
        fun getMaxListeners(): Int =
            maxListeners

        /**
         * How many listeners [eventName] has, including those added with `once`.
         *
         * @param eventName The event's name.
         */
        fun listenerCount(eventName: String): Int =
            listeners[eventName]?.size ?: 0

        /**
         * The listeners of [eventName], in the order they are called.
         *
         * @param eventName The event's name.
         * @return The listeners; empty when there are none.
         */
        fun listeners(eventName: String): Array<EventCallback> =
            listeners[eventName]?.map(CallbackData::callback)?.toTypedArray() ?: arrayOf()

        /**
         * Adds [listener] to be called each time [eventName] is emitted.
         *
         * It goes after the event's other listeners. A listener added twice is called twice.
         *
         * @param eventName The event's name.
         * @param listener Called with one argument, a Java array of the values given to `emit`.
         * @return This emitter, for chaining.
         */
        fun on(eventName: String, listener: EventCallback): EventEmitter {
            listeners.putIfAbsent(eventName, arrayListOf())
            listeners[eventName]!!.checkLimit {
                it += wrap(listener)
            }
            emit(EVENT_NEW_LISTENER, eventName, listener)
            return this
        }

        /**
         * Removes [listener] from the listeners of [eventName], or the most recently added one if [listener] is `null`.
         *
         * Listeners are compared as the Java objects they became when added. NeonJS makes a new one each time a JS
         * function is passed, so passing the same function again may find nothing to remove;
         * `removeAllListeners(eventName)` always works.
         *
         * @param eventName The event's name.
         * @param listener The listener to remove, or `null` for the most recently added one.
         * @return This emitter, for chaining.
         */
        fun off(eventName: String, listener: EventCallback?): EventEmitter {
            if (eventName !in listeners)
                return this

            if (listener == null)
                listeners[eventName]!!.removeLastOrNull()
            else
                listeners[eventName]!!
                    .removeIf { it.callback == listener }
                    .also { removed -> if (removed) emit(
                        EVENT_REMOVE_LISTENER, eventName, listener) }
            return this
        }

        /**
         * Adds [listener] to be called the next time [eventName] is emitted, and then removed.
         *
         * @param eventName The event's name.
         * @param listener Called with one argument, a Java array of the values given to `emit`.
         * @return This emitter, for chaining.
         */
        fun once(eventName: String, listener: EventCallback): EventEmitter {
            listeners.putIfAbsent(eventName, arrayListOf())
            listeners[eventName]!!.checkLimit {
                it += wrap(listener, isOnce = true)
            }
            emit(EVENT_NEW_LISTENER, eventName, listener)
            return this
        }

        /**
         * Adds [listener] before the other listeners of [eventName], to be called each time it is emitted.
         *
         * @param eventName The event's name.
         * @param listener Called with one argument, a Java array of the values given to `emit`.
         */
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

        /**
         * Adds [listener] before the other listeners of [eventName], to be called the next time it is emitted.
         *
         * If no listener was ever added for [eventName], it is added as an ordinary listener instead, and stays after
         * its first call.
         *
         * @param eventName The event's name.
         * @param listener Called with one argument, a Java array of the values given to `emit`.
         */
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

        /**
         * Removes every listener of [eventName], or of every event when it is left out or `null`.
         *
         * @param eventName The event's name, or `null` for every event.
         */
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

        /**
         * Removes [listener] from the listeners of [eventName]; the same as `off`.
         *
         * @param eventName The event's name.
         * @param listener The listener to remove, or `null` for the most recently added one.
         * @return This emitter, for chaining.
         */
        fun removeListener(eventName: String, listener: EventCallback?) =
            off(eventName, listener)

        /**
         * Sets how many listeners an event may have before adding another logs a warning.
         *
         * @param n The number; `0` or `Infinity` for no limit. A fraction is cut to a whole number.
         * @throws IllegalArgumentException If [n] is a negative whole number.
         */
        fun setMaxListeners(n: Int) {
            require(n >= 0) { "Max listener size must be n >= 0" }
            maxListeners = n
        }

        /**
         * The overload NeonJS picks for `Infinity` and fractions; scripts call it as `setMaxListeners(n)` either way.
         *
         * @suppress
         */
        fun setMaxListeners(n: Double) {
            maxListeners = if (n.isInfinite())
                0
            else
                n.toInt()
        }

        /**
         * The listeners of [eventName]; the same as `listeners`.
         *
         * @param eventName The event's name.
         */
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