/*
 * BotManagerApi.kt created by Minki Moon(mooner1022) on 8/19/23, 3:42 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.listener.event.Api2Events
import dev.mooner.starlight.listener.event.Api2StartCompileEvent
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import java.lang.reflect.Proxy
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A listener; it takes as many arguments as the event gives, up to three, and JavaScript ignores the rest. */
typealias Api2Listener = (arg1: Any?, arg2: Any?, arg3: Any?) -> Unit

class BotManagerApi: Api<BotManagerApi.BotManager>() {

    override val name: String = "BotManager"

    override val objects: List<ApiObject> =
        getApiObjects<BotManager>()

    override val instanceClass: Class<BotManager> =
        BotManager::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        BotManager(project)

    /** MessengerBot R's `BotManager`: the bots of this device's projects, one per compiled project. */
    class BotManager(
        private val project: Project
    ) {

        fun getCurrentBot(): Bot =
            Bot.of(project)

        /** The bot of the project named [botName]; `null` if there is none or it isn't compiled. */
        fun getBot(botName: String): Bot? =
            compiledProject(botName)?.let(Bot::of)

        fun getBotList(): Array<Bot> =
            compiledProjects().map(Bot::of).toTypedArray()

        fun getBotNames(): Array<String> =
            compiledProjects().map { it.info.name }.toTypedArray()

        fun getBotCount(): Int =
            compiledProjects().size

        /** The rooms bots can send to, from the messages received so far. [packageName] isn't told apart yet. */
        @JvmOverloads
        fun getRooms(packageName: String? = null): Array<Api2Room> =
            NotificationListener.getRooms().map(::Api2Room).toTypedArray()

        fun getPower(botName: String): Boolean =
            projectOf(botName)?.info?.isEnabled == true

        fun setPower(botName: String, power: Boolean) {
            projectOf(botName)?.setEnabled(power)
        }

        @JvmOverloads
        fun compile(botName: String, throwOnError: Boolean = false): Boolean =
            projectOf(botName)?.compile(throwOnError) == true

        fun compileAll() {
            for (project in Session.projectManager.getProjects())
                project.compile()
        }

        /** Compiles the project unless it was compiled: 0 if that failed, 1 if it succeeded, 2 if it was compiled. */
        @JvmOverloads
        fun prepare(botName: String, throwOnError: Boolean = false): Int =
            prepareActual(projectOf(botName), throwOnError)

        /** [prepare] for this project. */
        @JvmOverloads
        fun prepare(throwOnError: Boolean = false): Int =
            prepareActual(project, throwOnError)

        /** [prepare] for every project; how many it tried to compile, whether that worked or not. */
        @JvmOverloads
        fun prepareAll(throwOnError: Boolean = false): Int =
            Session.projectManager.getProjects()
                .filterNot(Project::isCompiled)
                .onEach { prepareActual(it, throwOnError) }
                .size

        fun isCompiled(botName: String): Boolean =
            projectOf(botName)?.isCompiled == true

        fun unload(botName: String) {
            projectOf(botName)?.destroy(requestUpdate = true)
        }

        /** Unloads this project. */
        fun unload() =
            project.destroy(requestUpdate = true)

        private fun prepareActual(project: Project?, throwOnError: Boolean): Int {
            if (project == null)
                return 0
            if (project.isCompiled)
                return 2
            return runCatching { project.compile(throwOnError) }
                .getOrElse { if (throwOnError) throw it else false }
                .let { if (it) 1 else 0 }
        }

        private fun projectOf(botName: String): Project? =
            Session.projectManager.getProjectByName(botName)

        private fun compiledProject(botName: String): Project? =
            projectOf(botName)?.takeIf(Project::isCompiled)

        private fun compiledProjects(): List<Project> =
            Session.projectManager.getProjects().filter(Project::isCompiled)
    }

    /**
     * MessengerBot R's `Bot`: one per compiled project, holding the listeners its script added. It lasts until the
     * project is compiled again or destroyed, and the listeners go with it. Events reach the listeners through the
     * project, so they follow its power switch and allowed events and run on its workers, like its other events.
     */
    class Bot private constructor(
        private val project: Project
    ) {

        private val listeners: MutableMap<String, MutableList<Registered>> = ConcurrentHashMap()

        /** A listener and what identifies the script function behind it, for [off]. */
        private class Registered(val listener: Api2Listener) {
            val identity: Any = identityOf(listener)
        }

        @Volatile
        private var commandPrefix: String? = null

        fun setCommandPrefix(prefix: String) {
            commandPrefix = prefix
        }

        /** Sends [msg] to [room] (see [roomOf]); whether the room could be found. [packageName] isn't used yet. */
        @JvmOverloads
        fun send(room: Any, msg: String, packageName: String? = null): Boolean =
            roomOf(room)?.send(msg) ?: false

        @JvmOverloads
        fun canReply(room: Any, packageName: String? = null): Boolean =
            roomOf(room) != null

        /** Marks [room] read, or the room the last message came from when it is `null`. */
        @JvmOverloads
        fun markAsRead(room: Any?, packageName: String? = null): Boolean =
            if (room == null)
                NotificationListener.markAsRead()
            else
                roomOf(room)?.markAsRead() ?: false

        fun getName(): String =
            project.info.name

        fun getRootPath(): String =
            project.directory.path

        fun setPower(power: Boolean) =
            project.setEnabled(power)

        fun getPower(): Boolean =
            project.info.isEnabled

        fun compile() =
            project.compile(throwException = true)

        fun unload() =
            project.destroy(requestUpdate = true)

        fun on(eventName: String, listener: Api2Listener) {
            listenersOf(eventName) += Registered(listener)
        }

        fun addListener(eventName: String, listener: Api2Listener) =
            on(eventName, listener)

        fun prependListener(eventName: String, listener: Api2Listener) =
            listenersOf(eventName).add(0, Registered(listener))

        /** Removes [listener], or every listener of [eventName] without one. */
        @JvmOverloads
        fun off(eventName: String, listener: Api2Listener? = null) {
            if (listener == null) {
                listeners.remove(eventName)
                return
            }
            val identity = identityOf(listener)
            listeners[eventName]?.let { registered -> registered.removeAll { it.identity == identity } }
        }

        @JvmOverloads
        fun removeListener(eventName: String, listener: Api2Listener? = null) =
            off(eventName, listener)

        fun removeAllListeners(eventName: String) {
            listeners.remove(eventName)
        }

        fun listeners(eventName: String): Array<Api2Listener> =
            listeners[eventName]?.map(Registered::listener)?.toTypedArray() ?: emptyArray()

        fun getListenersMap(): Map<String, List<Api2Listener>> =
            listeners.mapValues { (_, list) -> list.map(Registered::listener) }

        private fun listenersOf(eventName: String): MutableList<Registered> =
            listeners.getOrPut(eventName, ::CopyOnWriteArrayList)

        /** Calls the listeners of [eventName] in order, on the thread running the project's event. */
        internal fun deliver(eventName: String, arg1: Any? = null, arg2: Any? = null, arg3: Any? = null) {
            for (registered in listeners[eventName] ?: return)
                registered.listener(arg1, arg2, arg3)
        }

        internal fun deliverMessage(message: Message) =
            deliver(Api2Events.MESSAGE, Api2Message(message))

        /** Delivers [message] to the command listeners if it starts with the prefix the script set. */
        internal fun deliverCommand(message: Message) {
            val (command, args) = Api2Command.parse(message.message, commandPrefix) ?: return
            deliver(Api2Events.COMMAND, Api2Command(message, command, args))
        }

        /** The room [room] names: a room name, an ID, or a room from [BotManager.getRooms]. */
        private fun roomOf(room: Any): ChatRoom? {
            val key = when (room) {
                is Api2Room -> room.channelId?.toString() ?: room.name
                is BigInteger -> room.toString()
                is Double, is Float -> (room as Number).toLong().toString()
                else -> room.toString()
            }
            return NotificationListener.getRoom(key)
        }

        private val lifecycleObserver = object : ProjectLifecycleObserver.ExplicitObserver {
            // On the compile thread, before the new code runs, as onStartCompile is called for a function.
            override fun onCompileStart(project: Project) {
                if (Api2Events.isAllowed(project, Api2StartCompileEvent::class))
                    runCatching { deliver(Api2Events.START_COMPILE) }
                        .onFailure { project.logger.error("startCompile listener failed: $it") }
                close()
            }

            override fun onDestroy(project: Project) = close()
        }

        private fun close() {
            listeners.clear()
            project.getLifecycle().unregisterObserver(lifecycleObserver)
            bots.remove(project.info.id, this)
        }

        init {
            project.getLifecycle().registerObserver(lifecycleObserver)
        }

        companion object {

            private val bots: MutableMap<ProjectID, Bot> = ConcurrentHashMap()

            /**
             * The script function behind [listener]. NeonJS hands host code a new proxy each time a JS function is
             * passed, and its proxies equal only themselves, so the same function passed to [on] and [off] would not
             * match; the function each proxy stands for does. Rhino reuses one adapter per function.
             */
            private fun identityOf(listener: Any): Any {
                if (!Proxy.isProxyClass(listener.javaClass))
                    return listener
                val handler = Proxy.getInvocationHandler(listener)
                return runCatching { handler.javaClass.getMethod("getTarget").invoke(handler) }.getOrNull() ?: listener
            }

            /** The project's bot, made when first asked for. */
            internal fun of(project: Project): Bot =
                bots.computeIfAbsent(project.info.id) { Bot(project) }

            /** The project's bot, if its script asked for one. */
            internal fun find(project: Project): Bot? =
                bots[project.info.id]
        }
    }
}
