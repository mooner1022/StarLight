/*
 * BotManagerApi.kt created by Minki Moon(mooner1022) on 8/19/23, 3:42 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.api.ApiLevels
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.api.msgbot.scriptFunctionIdentity
import dev.mooner.starlight.listener.chat.ChatSessions
import dev.mooner.starlight.listener.chat.RoomResolver
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A listener; it takes as many arguments as the event gives, up to three, and JavaScript ignores the rest. */
typealias Api2Listener = (arg1: Any?, arg2: Any?, arg3: Any?) -> Unit

/** [value] as text when it is a string, `null` for anything else (numbers, booleans and objects included). */
private fun stringOnly(value: Any?): String? = when {
    value is CharSequence                -> value.toString()
    value is NeonValue && value.isString -> value.asString()
    else                                 -> null
}

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
     * Bots are named by their projects' names, matched exactly. Only a compiled project whose API levels include
     * MessengerBot R's API2 counts as a bot, whether or not it is on. A name may be given as any value: a number or a
     * boolean is read as its text, and `null` or `undefined` throws `"botName must be a string"`.
     *
     * Compiling never throws here: a project that fails to compile is reported through the return value and its logs,
     * whatever `throwOnError` says.
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
         * The bot of the project named [botName]; `null` if there is none, it isn't compiled or it doesn't use API2.
         *
         * @param botName The project's name.
         * @throws IllegalArgumentException `"botName must be a string"` when [botName] is `null` or `undefined`.
         */
        fun getBot(botName: Any?): Bot? =
            compiledBotProject(botName)?.let(Bot::of)

        /** The bots of the compiled projects that use API2. */
        fun getBotList(): Array<Bot> =
            botProjects().map(Bot::of).toTypedArray()

        /** The names of the compiled projects that use API2. */
        fun getBotNames(): Array<String> =
            botProjects().map { it.info.name }.toTypedArray()

        /** How many compiled projects use API2. */
        fun getBotCount(): Int =
            botProjects().size

        /**
         * The rooms bots can send to: the rooms StarLight has received a message from since it started, in every app
         * the notification rules cover, and the rooms a script bound with `sessionManager.bindSession`.
         *
         * The debug room is never one. Each room object has `name` and `id` and can be passed back to `bot.send`,
         * `bot.canReply`, `bot.markAsRead` and `bot.sendMarkdown`.
         *
         * @param packageName An app's package name to list only the rooms of that app, such as `com.kakao.talk`; an
         * app without rooms gives an empty array. Leave it out, or pass anything but a string, for every room.
         * @return The rooms, in the order they were first seen; in no particular order when [packageName] is given.
         */
        @JvmOverloads
        fun getRooms(packageName: Any? = null): Array<Api2Room> {
            val registry = ChatSessions.registry
            val keys = stringOnly(packageName)?.let(registry::roomsOf) ?: registry.rooms()
            return keys.map { Api2Room(it, registry.chatRoom(it)) }.toTypedArray()
        }

        /** Every room bots can send to; the same as `getRooms()`. */
        fun getAllRooms(): Array<Api2Room> =
            getRooms()

        /**
         * Whether the project named [botName] is on.
         *
         * @param botName The project's name.
         * @return `false` also when there is no such project.
         * @throws IllegalArgumentException `"botName must be a string"` when [botName] is `null` or `undefined`.
         */
        fun getPower(botName: Any?): Boolean =
            projectOf(botName)?.info?.isEnabled == true

        /**
         * Turns the project named [botName] on or off, as its switch in the app does.
         *
         * Nothing happens when there is no such project or it isn't compiled.
         *
         * @param botName The project's name.
         * @param power `true` to turn it on, `false` to turn it off. A number is `true` unless it is 0, the strings
         * `"true"` and `"false"` count as booleans, and `null` or `undefined` is `false`.
         * @throws IllegalArgumentException `"power must be a boolean"` for any other kind of value.
         */
        fun setPower(botName: Any?, power: Any?) {
            val enabled = ScriptArgs.boolean(power, "power", false)
            projectOf(botName)?.setEnabled(enabled)
        }

        /**
         * Compiles the project named [botName] and waits until that is done.
         *
         * Compiling doesn't turn the project on. Naming this script's own project replaces the code that is running.
         * It never throws: a failed compile is written to that project's logs.
         *
         * @param botName The project's name.
         * @param throwOnError Accepted for MessengerBot R's sake and ignored.
         * @return Whether it compiled; `false` when there is no such project.
         * @see guide:projects#compiling-and-turning-on
         */
        @JvmOverloads
        fun compile(botName: Any?, throwOnError: Any? = null): Boolean {
            ScriptArgs.boolean(throwOnError, "throwOnError", false)
            return compileSafely(projectOf(botName))
        }

        /**
         * Compiles every project, one after another, and waits until that is done.
         *
         * This script's own project is compiled too, which replaces the code that is running. A failed compile isn't
         * thrown; it is written to that project's logs.
         */
        fun compileAll() {
            for (project in Session.projectManager.getProjects())
                compileSafely(project)
        }

        /**
         * Compiles the project named [botName] unless it is compiled: 0 if that failed, 1 if it succeeded, 2 if it was
         * compiled.
         *
         * A failed compile isn't thrown, whatever `throwOnError` says; it is written to the project's logs. Passing a
         * boolean alone, as `BotManager.prepare(true)`, prepares this script's own project.
         *
         * @param botName The project's name; 0 is returned when there is no such project.
         * @param throwOnError Accepted for MessengerBot R's sake and ignored.
         */
        @JvmOverloads
        fun prepare(botName: Any?, throwOnError: Any? = null): Int {
            if (botName is Boolean)
                return prepareActual(project)
            ScriptArgs.boolean(throwOnError, "throwOnError", false)
            return prepareActual(projectOf(botName))
        }

        /** [prepare] for this project. */
        fun prepare(): Int =
            prepareActual(project)

        /**
         * [prepare] for every project that isn't compiled; how many it tried to compile, whether that worked or not.
         *
         * @param throwOnError Accepted for MessengerBot R's sake and ignored: a failed compile is never thrown.
         */
        @JvmOverloads
        fun prepareAll(throwOnError: Any? = null): Int {
            ScriptArgs.boolean(throwOnError, "throwOnError", false)
            return Session.projectManager.getProjects()
                .filterNot(Project::isCompiled)
                .onEach { prepareActual(it) }
                .size
        }

        /**
         * Whether the project named [botName] is compiled.
         *
         * @param botName The project's name.
         * @return `false` also when there is no such project.
         * @throws IllegalArgumentException `"botName must be a string"` when [botName] is `null` or `undefined`.
         */
        fun isCompiled(botName: Any?): Boolean =
            projectOf(botName)?.isCompiled == true

        /**
         * Unloads the project named [botName]: stops what it is running, discards its compiled code and turns it off.
         *
         * Nothing happens when there is no such project. Naming this script's own project stops this script.
         *
         * @param botName The project's name.
         */
        fun unload(botName: Any?) {
            projectOf(botName)?.destroy(requestUpdate = true)
        }

        /** Unloads this project: stops what it is running, discards its compiled code and turns it off. */
        fun unload() =
            project.destroy(requestUpdate = true)

        private fun compileSafely(project: Project?): Boolean {
            if (project == null)
                return false
            return try {
                project.compileForScript(throwException = false)
            } catch (e: Throwable) {
                project.logger.error("Compiling failed: $e")
                false
            }
        }

        private fun prepareActual(project: Project?): Int {
            if (project == null)
                return 0
            if (project.isCompiled)
                return 2
            return if (compileSafely(project)) 1 else 0
        }

        private fun projectOf(botName: Any?): Project? =
            Session.projectManager.getProjectByName(ScriptArgs.string(botName, "botName"))

        private fun compiledBotProject(botName: Any?): Project? =
            projectOf(botName)?.takeIf { it.isCompiled && usesApi2(it) }

        private fun botProjects(): List<Project> =
            Session.projectManager.getProjects().filter { it.isCompiled && usesApi2(it) }

        private fun usesApi2(project: Project): Boolean =
            project.info.apiLevels?.contains(ApiLevels.API2) ?: true
    }

    /**
     * MessengerBot R's `Bot`: one per compiled project, holding the listeners its script added. It lasts until the
     * project is compiled again or destroyed, and the listeners go with it. Events reach the listeners through the
     * project, so they follow its power switch and allowed events and run on its workers, like its other events.
     *
     * A room is named as `BotManager.getRooms()` gives it, by name (a string is only ever a name, even of digits), by ID
     * (a BigInt or a whole number), or as a pair `[name, id]`. A `packageName` limits the call to that app's session of
     * the room. A message is any value: it is sent as its text, `null` as `"null"`.
     *
     * @see guide:events#api2-listeners
     */
    class Bot private constructor(
        private val project: Project
    ) {

        private val listeners: MutableMap<String, MutableList<Registered>> = ConcurrentHashMap()

        /** How many listeners the script has added, of any event. */
        internal val listenerCount: Int
            get() = listeners.values.sumOf { it.size }

        /** A listener and what identifies the script function behind it, for [off]. */
        private class Registered(val listener: Api2Listener) {
            val identity: Any = scriptFunctionIdentity(listener)
        }

        @Volatile
        private var prefix: String = ""

        /** The debug room the latest debug-room message came from, which sends to its name or ID land in. */
        @Volatile
        private var debugRoom: ChatRoom? = null

        /** The wrapper of the latest message delivered, so a command made of it shares its sender. */
        @Volatile
        private var lastMessage: Api2Message? = null

        /**
         * Sets the prefix that makes a message a command for this bot's `command` listeners.
         *
         * A message whose first word (the text up to the first space) starts with [prefix] reaches `command` listeners as
         * an `Api2Command`, and `message` listeners as usual. The prefix is `""` until it is set, which makes every
         * message a command.
         *
         * Call it as `bot.setCommandPrefix("/")`; NeonJS also takes `bot.commandPrefix = "/"`. Compiling the project
         * again starts with `""`.
         *
         * @param prefix The text a command starts with, such as `"/"`; not `null`.
         */
        fun setCommandPrefix(prefix: String) {
            this.prefix = prefix
        }

        /** The text a message must start with to be a command; `""` until [setCommandPrefix] sets one. */
        fun getCommandPrefix(): String =
            prefix

        /**
         * Sends [msg] to [room].
         *
         * The room must be one StarLight has received a message from since it started, as `BotManager.getRooms()` lists
         * them, or one a script bound. The one exception is the debug room: after a message from it, sending to its name
         * or ID shows [msg] on the debug screen and returns `true`.
         *
         * @param room A room name, a room ID (a BigInt or a whole number), a `[name, id]` pair or a room object.
         * @param msg The text to send; any other value is sent as its text.
         * @param packageName The messenger's package name, to send through that app's session of the room. Anything but a
         * string counts as not given.
         * @return Whether the room has a session to send through, or is the debug room; `false` when the room isn't
         * known or has no session in [packageName], or when the room's reply action has expired (its notification was
         * dismissed). `true` doesn't mean the messenger delivered it.
         * @see guide:testing#how-debug-messages-differ
         */
        @JvmOverloads
        fun send(room: Any?, msg: Any?, packageName: Any? = null): Boolean {
            val text = ScriptArgs.stringify(msg)
            val key = RoomResolver.resolve(room)
                ?: return sendToDebugRoom(room, text)
            return ChatSessions.reply(key, text, stringOnly(packageName), cancelledIsSent = false)
        }

        /**
         * Sends [msg] to [room] as a Markdown message.
         *
         * Messengers may refuse such a message; `true` only means it was handed over. KakaoTalk sends it, but shows
         * the formatting only on this device: the other people in the room see plain text (KakaoTalk 26.9.1). Without
         * [packageName], the app that has the room is used. Sending to the debug room shows [msg] on its screen.
         *
         * @param room A room name, a room ID (a BigInt or a whole number), a `[name, id]` pair or a room object.
         * @param msg The text to send; any other value is sent as its text.
         * @param packageName The messenger's package name. Anything but a string counts as not given.
         * @return Whether the share was launched; `false` when the room is unknown or no app can take it.
         */
        @JvmOverloads
        fun sendMarkdown(room: Any?, msg: Any?, packageName: Any? = null): Boolean {
            val text = ScriptArgs.stringify(msg)
            val key = RoomResolver.resolve(room)
                ?: return sendToDebugRoom(room, text)
            return ChatSessions.sendMarkdown(key, text, stringOnly(packageName))
        }

        /**
         * Whether the bot can send to [room]: whether a reply session exists for it, in [packageName] if one is given.
         *
         * The debug room is never such a room.
         *
         * @param room A room name, a room ID (a BigInt or a whole number), a `[name, id]` pair or a room object.
         * @param packageName The messenger's package name. Anything but a string counts as not given.
         */
        @JvmOverloads
        fun canReply(room: Any?, packageName: Any? = null): Boolean =
            ChatSessions.canReply(room, stringOnly(packageName))

        /**
         * Marks [room] read.
         *
         * @param room A room name, a room ID (a BigInt or a whole number), a `[name, id]` pair or a room object.
         * `null` marks nothing and returns `false`.
         * @param packageName The messenger's package name, to use that app's read action only. Without it, every app
         * that has the room is asked. Anything but a string counts as not given.
         * @return Whether a read action was sent; `false` when the room isn't known or has none.
         */
        @JvmOverloads
        fun markAsRead(room: Any?, packageName: Any? = null): Boolean =
            ChatSessions.markRoomAsRead(room, stringOnly(packageName))

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
         * @param power `true` to turn it on, `false` to turn it off. Boolean-like values are accepted as in
         * `BotManager.setPower`: a number is `true` unless it is 0, the strings `"true"` and `"false"` count as
         * booleans, and `null` or `undefined` is `false`.
         * @return Whether it applied; `false` when the project isn't compiled, which leaves it as it was.
         * @throws IllegalArgumentException `"enabled must be a boolean"` for an object.
         */
        fun setPower(power: Any?): Boolean =
            project.setEnabled(ScriptArgs.boolean(power, "enabled", false))

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
         * It never throws: a failed compile is written to the project's logs. From the project's own script, this
         * replaces the code that is running, and this bot's listeners are removed.
         *
         * @return Whether it compiled.
         * @see guide:projects#compiling-and-turning-on
         */
        fun compile(): Boolean =
            try {
                project.compileForScript(throwException = false)
            } catch (e: Throwable) {
                project.logger.error("Compiling failed: $e")
                false
            }

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
         * Removes [listener] from the event [eventName]: only the first time it was added; or, without a listener, the
         * last listener added to the event.
         *
         * Pass the same function that was added. A function added twice stays once after one call. Nothing happens when
         * the event has no such listener.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @param listener The function to remove; leave it out to remove the event's last listener.
         */
        @JvmOverloads
        fun off(eventName: String, listener: Api2Listener? = null) {
            val list = listeners[eventName] ?: return
            synchronized(list) {
                if (listener == null) {
                    if (list.isNotEmpty())
                        list.removeAt(list.size - 1)
                    return
                }
                val identity = scriptFunctionIdentity(listener)
                val index = list.indexOfFirst { it.identity == identity }
                if (index >= 0)
                    list.removeAt(index)
            }
        }

        /**
         * Removes [listener] from the event [eventName], or the event's last listener without one; the same as `off`.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @param listener The function to remove; leave it out to remove the event's last listener.
         */
        @JvmOverloads
        fun removeListener(eventName: String, listener: Api2Listener? = null) =
            off(eventName, listener)

        /**
         * Removes every listener of the event [eventName], or of every event without one.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`). Leave it out, or pass anything but a
         * string, to remove the listeners of all events.
         */
        @JvmOverloads
        fun removeAllListeners(eventName: Any? = null) {
            val name = stringOnly(eventName)
            if (name == null)
                listeners.clear()
            else
                listeners.remove(name)
        }

        /**
         * The listeners of the event [eventName], in the order they are called.
         *
         * @param eventName The event's name, such as `Event.MESSAGE` (`"message"`).
         * @return A copy, empty when there are none; `null` when [eventName] is missing or not a string. In NeonJS its
         * items are the functions that were added.
         */
        @JvmOverloads
        fun listeners(eventName: Any? = null): Array<Api2Listener>? {
            val name = stringOnly(eventName) ?: return null
            return listeners[name]?.map(Registered::listener)?.toTypedArray() ?: emptyArray()
        }

        /**
         * Every event's listeners, by event name, each list in the order they are called.
         *
         * A copy: changing it doesn't change the bot. In NeonJS the listeners are the functions that were added.
         */
        fun getListenersMap(): Map<String, List<Api2Listener>> =
            listeners.mapValues { (_, list) -> list.map(Registered::listener) }

        /** Removes every listener of every event. */
        fun cleanup() {
            listeners.clear()
        }

        private fun listenersOf(eventName: String): MutableList<Registered> =
            listeners.getOrPut(eventName, ::CopyOnWriteArrayList)

        /** Calls the listeners of [eventName] in order, on the thread running the project's event. */
        internal fun deliver(eventName: String, arg1: Any? = null, arg2: Any? = null, arg3: Any? = null) {
            for (registered in listeners[eventName] ?: return)
                registered.listener(arg1, arg2, arg3)
        }

        internal fun deliverMessage(message: Message) {
            if (message.room.isDebugRoom)
                debugRoom = message.room
            val wrapped = Api2Message(message)
            lastMessage = wrapped
            deliver(Api2Events.MESSAGE, wrapped)
        }

        /** Delivers [message] to the command listeners if its first word starts with the bot's prefix. */
        internal fun deliverCommand(message: Message) {
            if (message.room.isDebugRoom)
                debugRoom = message.room
            val (command, args) = Api2Command.parse(message.message, prefix) ?: return
            val author = lastMessage?.takeIf { it.source === message }?.author
            deliver(Api2Events.COMMAND, Api2Command(message, command, args, author))
        }

        /**
         * Shows [text] in the debug room if [room] names it, by its name or its ID, as MessengerBot R does; `false`
         * when it doesn't.
         */
        private fun sendToDebugRoom(room: Any?, text: String): Boolean {
            val debug = debugRoom ?: return false
            val name = stringOnly(room)
            val id = ScriptArgs.integral(room) ?: name?.trim()?.toBigIntegerOrNull()
            val matches = (name != null && debug.name == name) ||
                (id != null && debug.id.trim().toBigIntegerOrNull() == id)
            if (!matches)
                return false
            runCatching { debug.send(text) }
            return true
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

            /** The project's bot, made when first asked for. */
            internal fun of(project: Project): Bot =
                bots.computeIfAbsent(project.info.id) { Bot(project) }

            /** The project's bot, if its script asked for one. */
            internal fun find(project: Project): Bot? =
                bots[project.info.id]
        }
    }
}
