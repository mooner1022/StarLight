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
import dev.mooner.starlight.languages.neonjs.compileForScript
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

    /**
     * MessengerBot R's `BotManager`: the bots of this device's projects, one per compiled project; a global in API2
     * projects.
     *
     * Bots are named by their projects' names, matched exactly. Every compiled project counts as a bot, whatever its
     * API levels and whether it is on. The `packageName` arguments MessengerBot R takes are accepted and ignored.
     *
     * @see guide:events#api2-listeners
     */
    class BotManager(
        private val project: Project
    ) {

        /**
         * The bot of the project running this script, which holds its API2 listeners.
         *
         * API2 events are delivered only once the script has its bot, so start with
         * `const bot = BotManager.getCurrentBot();` at the top level. It is the same bot until the project compiles
         * again or is unloaded.
         */
        fun getCurrentBot(): Bot =
            Bot.of(project)

        /**
         * The bot of the project named [botName]; `null` if there is none or it isn't compiled.
         *
         * @param botName The project's name.
         */
        fun getBot(botName: String): Bot? =
            compiledProject(botName)?.let(Bot::of)

        /** The bots of the compiled projects. */
        fun getBotList(): Array<Bot> =
            compiledProjects().map(Bot::of).toTypedArray()

        /** The names of the compiled projects. */
        fun getBotNames(): Array<String> =
            compiledProjects().map { it.info.name }.toTypedArray()

        /** How many projects are compiled. */
        fun getBotCount(): Int =
            compiledProjects().size

        /**
         * The rooms bots can send to: the rooms StarLight has received a message from since it started.
         *
         * Only rooms of the first notification rule's app are kept, KakaoTalk by default. The debug room is never one.
         *
         * @param packageName The app to list rooms of. Ignored: every kept room is returned.
         * @return The rooms, in no particular order.
         */
        @JvmOverloads
        fun getRooms(packageName: String? = null): Array<Api2Room> =
            NotificationListener.getRooms().map(::Api2Room).toTypedArray()

        /**
         * Whether the project named [botName] is on.
         *
         * @param botName The project's name.
         * @return `false` also when there is no such project.
         */
        fun getPower(botName: String): Boolean =
            projectOf(botName)?.info?.isEnabled == true

        /**
         * Turns the project named [botName] on or off, as its switch in the app does.
         *
         * Nothing happens when there is no such project or it isn't compiled.
         *
         * @param botName The project's name.
         * @param power `true` to turn it on, `false` to turn it off.
         */
        fun setPower(botName: String, power: Boolean) {
            projectOf(botName)?.setEnabled(power)
        }

        /**
         * Compiles the project named [botName] and waits until that is done.
         *
         * Compiling doesn't turn the project on. Naming this script's own project replaces the code that is running.
         *
         * @param botName The project's name.
         * @param throwOnError Whether a failed compile throws its error rather than returning `false`.
         * @return Whether it compiled; `false` when there is no such project.
         * @throws Exception The compile error, when [throwOnError] is `true`.
         * @see guide:projects#compiling-and-turning-on
         */
        @JvmOverloads
        fun compile(botName: String, throwOnError: Boolean = false): Boolean =
            projectOf(botName)?.compileForScript(throwOnError) == true

        /**
         * Compiles every project, one after another, and waits until that is done.
         *
         * This script's own project is compiled too, which replaces the code that is running. A failed compile isn't
         * thrown; it is written to that project's logs.
         */
        fun compileAll() {
            for (project in Session.projectManager.getProjects())
                project.compileForScript()
        }

        /**
         * Compiles the project named [botName] unless it is compiled: 0 if that failed, 1 if it succeeded, 2 if it was
         * compiled.
         *
         * @param botName The project's name; 0 is returned when there is no such project.
         * @param throwOnError Whether a failed compile throws its error rather than returning 0.
         * @throws Exception The compile error, when [throwOnError] is `true`.
         */
        @JvmOverloads
        fun prepare(botName: String, throwOnError: Boolean = false): Int =
            prepareActual(projectOf(botName), throwOnError)

        /**
         * [prepare] for this project.
         *
         * @param throwOnError Whether a failed compile throws its error rather than returning 0.
         */
        @JvmOverloads
        fun prepare(throwOnError: Boolean = false): Int =
            prepareActual(project, throwOnError)

        /**
         * [prepare] for every project; how many it tried to compile, whether that worked or not.
         *
         * @param throwOnError Whether a failed compile throws its error, stopping there, rather than going on.
         */
        @JvmOverloads
        fun prepareAll(throwOnError: Boolean = false): Int =
            Session.projectManager.getProjects()
                .filterNot(Project::isCompiled)
                .onEach { prepareActual(it, throwOnError) }
                .size

        /**
         * Whether the project named [botName] is compiled.
         *
         * @param botName The project's name.
         * @return `false` also when there is no such project.
         */
        fun isCompiled(botName: String): Boolean =
            projectOf(botName)?.isCompiled == true

        /**
         * Unloads the project named [botName]: stops what it is running, discards its compiled code and turns it off.
         *
         * Nothing happens when there is no such project. Naming this script's own project stops this script.
         *
         * @param botName The project's name.
         */
        fun unload(botName: String) {
            projectOf(botName)?.destroy(requestUpdate = true)
        }

        /** Unloads this project: stops what it is running, discards its compiled code and turns it off. */
        fun unload() =
            project.destroy(requestUpdate = true)

        private fun prepareActual(project: Project?, throwOnError: Boolean): Int {
            if (project == null)
                return 0
            if (project.isCompiled)
                return 2
            return runCatching { project.compileForScript(throwOnError) }
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
     *
     * The `packageName` arguments MessengerBot R takes are accepted and ignored.
     *
     * @see guide:events#api2-listeners
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

        /**
         * Sets the prefix that makes a message a command for this bot's `command` listeners.
         *
         * A message that starts with [prefix] and has a word after it reaches `command` listeners as an `Api2Command`,
         * and `message` listeners as usual. Until a prefix is set, or while it is `""`, no message is a command.
         *
         * Call it as `bot.setCommandPrefix("/")`; NeonJS also takes `bot.commandPrefix = "/"`. It can't be read back.
         * Compiling the project again starts without one.
         *
         * @param prefix The text a command starts with, such as `"/"`.
         */
        fun setCommandPrefix(prefix: String) {
            commandPrefix = prefix
        }

        /**
         * Sends [msg] to [room].
         *
         * The room must be one StarLight has received a message from since it started, as `BotManager.getRooms()`
         * lists them; the debug room isn't one, so in tests this returns `false`.
         *
         * @param room A room name, a room ID, or an `Api2Room`. Give a 64-bit ID as a BigInt or a string, not a number.
         * @param msg The text to send.
         * @param packageName The messenger's package name. Ignored.
         * @return Whether it was handed to the messenger; `false` when the room isn't known or no longer takes replies.
         * @see guide:testing#how-debug-messages-differ
         */
        @JvmOverloads
        fun send(room: Any, msg: String, packageName: String? = null): Boolean =
            roomOf(room)?.send(msg) ?: false

        /**
         * Whether the bot can send to [room]: whether StarLight has received a message from it since it started.
         *
         * The debug room is never such a room.
         *
         * @param room A room name, a room ID, or an `Api2Room`. Give a 64-bit ID as a BigInt or a string, not a number.
         * @param packageName The messenger's package name. Ignored.
         */
        @JvmOverloads
        fun canReply(room: Any, packageName: String? = null): Boolean =
            roomOf(room) != null

        /**
         * Marks [room] read, or the room the last message came from when it is `null`.
         *
         * @param room A room name, a room ID, an `Api2Room`, or `null`. Give a 64-bit ID as a BigInt or a string, not a
         * number.
         * @param packageName The messenger's package name. Ignored.
         * @return Whether the messenger took it; `false` when the room isn't known or its read action has expired.
         */
        @JvmOverloads
        fun markAsRead(room: Any?, packageName: String? = null): Boolean =
            if (room == null)
                NotificationListener.markAsRead()
            else
                roomOf(room)?.markAsRead() ?: false

        /** The bot's name, which is its project's name. */
        fun getName(): String =
            project.info.name

        /**
         * The absolute path of the bot's project folder.
         *
         * @see guide:projects#files
         */
        fun getRootPath(): String =
            project.directory.path

        /**
         * Turns the bot's project on or off, as its switch in the app does.
         *
         * @param power `true` to turn it on, `false` to turn it off.
         * @return Whether it applied; `false` when the project isn't compiled, which leaves it as it was.
         */
        fun setPower(power: Boolean) =
            project.setEnabled(power)

        /**
         * Whether the bot's project is on.
         *
         * Setting it, as `bot.setPower(false)` or `bot.power = false`, turns the project on or off as its switch in the
         * app does. That changes nothing while the project isn't compiled.
         */
        fun getPower(): Boolean =
            project.info.isEnabled

        /**
         * Compiles the bot's project again and waits until that is done.
         *
         * Unlike `BotManager.compile`, a failed compile throws. From the project's own script, this replaces the code
         * that is running, and this bot's listeners are removed.
         *
         * @return `true` once it compiled.
         * @throws Exception The compile error.
         * @see guide:projects#compiling-and-turning-on
         */
        fun compile() =
            project.compileForScript(throwException = true)

        /**
         * Unloads the bot's project: stops what it is running, discards its compiled code and turns it off.
         *
         * From the project's own script, this stops the script.
         */
        fun unload() =
            project.destroy(requestUpdate = true)

        /**
         * Adds [listener] for the event [eventName], after the listeners it has.
         *
         * Name the event with an `Event` constant, such as `Event.MESSAGE`; the listener receives that event's
         * arguments, as `describe_event` of its event ID declares them (`MessageListener` for `Event.MESSAGE`, in
         * `starlight.api2.message`). It is called only when the project receives the matching API2 event: its allowed events and API
         * levels include it, and the project is on, except for the debug room and `startCompile`. Names StarLight
         * doesn't fire are accepted and never called.
         *
         * An event's listeners run one after another in their list's order, on the project's workers (`startCompile`
         * ones on the compiling thread), and stay until the project compiles again or is unloaded. A function added
         * twice is called twice. An error thrown by a listener skips the listeners after it for that event, and is
         * handled as an event function's error: with the project's default settings it unloads the project, which
         * then has to be compiled again (guide:projects#errors).
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @param listener The function to call with the event's arguments.
         * @see guide:events#workers-and-order
         * @see guide:projects#errors
         * @listeners starlight.api2
         */
        fun on(eventName: String, listener: Api2Listener) {
            listenersOf(eventName) += Registered(listener)
        }

        /**
         * Adds [listener] for the event [eventName], after the listeners it has; the same as `on`.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @param listener The function to call with the event's arguments.
         * @listeners starlight.api2
         */
        fun addListener(eventName: String, listener: Api2Listener) =
            on(eventName, listener)

        /**
         * Adds [listener] for the event [eventName], before the listeners it has.
         *
         * Otherwise it is the same as `on`.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @param listener The function to call with the event's arguments.
         * @listeners starlight.api2
         */
        fun prependListener(eventName: String, listener: Api2Listener) =
            listenersOf(eventName).add(0, Registered(listener))

        /**
         * Removes [listener] from the event [eventName], or every listener of [eventName] without one.
         *
         * Pass the same function that was added; every time it was added is removed.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @param listener The function to remove; leave it out to remove them all.
         */
        @JvmOverloads
        fun off(eventName: String, listener: Api2Listener? = null) {
            if (listener == null) {
                listeners.remove(eventName)
                return
            }
            val identity = identityOf(listener)
            listeners[eventName]?.let { registered -> registered.removeAll { it.identity == identity } }
        }

        /**
         * Removes [listener] from the event [eventName], or every listener of [eventName] without one; the same as
         * `off`.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @param listener The function to remove; leave it out to remove them all.
         */
        @JvmOverloads
        fun removeListener(eventName: String, listener: Api2Listener? = null) =
            off(eventName, listener)

        /**
         * Removes every listener of the event [eventName].
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         */
        fun removeAllListeners(eventName: String) {
            listeners.remove(eventName)
        }

        /**
         * The listeners of the event [eventName], in the order they are called.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @return A copy, empty when there are none. In NeonJS its items are the functions that were added.
         */
        fun listeners(eventName: String): Array<Api2Listener> =
            listeners[eventName]?.map(Registered::listener)?.toTypedArray() ?: emptyArray()

        /**
         * Every event's listeners, by event name, each list in the order they are called.
         *
         * A copy: changing it doesn't change the bot. In NeonJS the listeners are the functions that were added.
         */
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
