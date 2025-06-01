/*
 * BotManagerApi.kt created by Minki Moon(mooner1022) on 8/19/23, 3:42 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import android.graphics.Bitmap
import dev.mooner.starlight.api.api2.BotManagerApi.Bot.MessageData.Author
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import dev.mooner.starlight.plugincore.utils.toBase64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private typealias ListenerCallback = (arg1: Any?, arg2: Any?) -> Unit // Can't use vararg

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

    class BotManager(
        private val project: Project
    ): ProjectLifecycleObserver.ExplicitObserver {

        companion object {
            private val botInstances: MutableMap<String, Bot> = hashMapOf()
        }

        private val _currentBot: Bot by lazy { Bot.fromProject(project) }

        init {
            project.getLifecycle().registerObserver(this)
        }

        override fun onCompileStart(project: Project) {
            botInstances -= project.info.name
            project.getLifecycle().unregisterObserver(this)
        }

        override fun onDestroy(project: Project) {
            botInstances -= project.info.name
            project.getLifecycle().unregisterObserver(this)
        }

        fun getCurrentBot(): Bot {
            return _currentBot
        }

        fun getBot(botName: String): Bot? =
            botInstances[botName] ?: Session.projectManager
                .getProjectByName(botName)
                ?.let(Bot::fromProject)
                ?.also { botInstances[botName] = it }

        @JvmOverloads
        fun getRooms(packageName: String? = null): Array<String> =
            NotificationListener.getRoomNames().toTypedArray()

        fun getBotList(): List<Bot> =
            botInstances.values.toList()

        fun getPower(botName: String): Boolean =
            Session.projectManager.getProjectByName(botName)?.info?.isEnabled == true

        fun setPower(botName: String, power: Boolean) {
            Session.projectManager.getProjectByName(botName)
                ?.setEnabled(power)
        }

        @JvmOverloads
        fun compile(botName: String, throwOnError: Boolean = false): Boolean =
            Session.projectManager.getProjectByName(botName)?.compile(throwOnError) == true

        fun compileAll() {
            for (proj in Session.projectManager.getProjects()) {
                proj.compile()
            }
        }

        private fun prepareActual(project: Project?, throwOnError: Boolean): Int {
            if (project == null)
                return 0
            if (project.isCompiled)
                return 2

            return runCatching {
                project.compile(throwOnError)
            }.getOrElse {
                if (throwOnError)
                    throw it
                false
            }.let {
                if (it) 1 else 0
            }
        }

        @JvmOverloads
        fun prepare(scriptName: String, throwOnError: Boolean = false): Int =
            prepareActual(Session.projectManager.getProjectByName(scriptName), throwOnError)

        @JvmOverloads
        fun prepare(throwOnError: Boolean = false): Int =
            prepareActual(project, throwOnError)

        fun isCompiled(botName: String): Boolean =
            Session.projectManager.getProjectByName(botName)?.isCompiled == true

        fun unload() =
            getCurrentBot().unload()
    }

    class Bot private constructor(
        private val project: Project
    ) {

        companion object {
            fun fromProject(project: Project): Bot =
                Bot(project)
        }

        private val eventScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val eventFlow: Flow<EventData> = MutableSharedFlow()
        private val listeners: MutableMap<String, ArrayDeque<Pair<ListenerCallback, Job>>> = hashMapOf()

        private var commandPrefix: String? = null

        fun setCommandPrefix(prefix: String) {
            commandPrefix = prefix
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
            val job = eventFlow
                .filter { it.name == eventName }
                .onEach { listener(it.args.getOrNull(0), it.args.getOrNull(1)) }
                .launchIn(eventScope)
            listeners.getOrPut(eventName, ::ArrayDeque) += listener to job
            //emitter.on(eventName, wrapListener(listener))
        }

        fun addListener(eventName: String, listener: ListenerCallback) =
            on(eventName, listener)

        @JvmOverloads
        fun off(eventName: String, listener: ListenerCallback? = null) {
            if (listener == null)
                listeners[eventName]?.last()?.second?.cancel()
            else
                listeners[eventName]?.find { it.first == listener }?.second?.cancel()
        }

        @JvmOverloads
        fun removeListener(eventName: String, listener: ListenerCallback? = null) =
            off(eventName, listener)

        fun removeAllListeners(eventName: String) {
            for ((_, job) in listeners[eventName] ?: emptyList()) {
                job.cancel()
            }
            listeners.remove(eventName)
        }

        fun prependListener(eventName: String, listener: ListenerCallback) {
            val job = eventFlow
                .filter { it.name == eventName }
                .onEach { listener(it.args.getOrNull(0), it.args.getOrNull(1)) }
                .launchIn(eventScope)
            listeners.getOrPut(eventName, ::ArrayDeque).addFirst(listener to job)
        }

        fun listeners(eventName: String): List<ListenerCallback> =
            listeners[eventName]?.map { it.first } ?: emptyList()

        private suspend fun handleMessageCreate(message: Message) {
            (eventFlow as MutableSharedFlow).emit(
                EventData(
                    name = "message",
                    args = listOf(MessageData.fromMessage(message))
                )
            )
            if (commandPrefix != null) {
                if (!message.message.startsWith('/') || !message.message.drop(1).startsWith(commandPrefix!!))
                    return

                val command = commandPrefix!!
                val args = message.message.drop(commandPrefix!!.length + 1).split(' ')
                eventFlow.emit(
                    EventData(
                        name = "command",
                        args = listOf(CommandData.fromMessage(command, args, message))
                    )
                )
            }
        }

        private suspend fun onMessageCreate(event: Events.Message.Create) =
            handleMessageCreate(event.message)

        private suspend fun onDebugRoomMessageCreate(event: ApplicationEvent.DebugRoom.MessageCreate) =
            handleMessageCreate(event.message)

        private suspend fun onNotificationPosted(event: Events.Notification.Post) {
            if ("notificationPosted" !in listeners)
                return
            (eventFlow as MutableSharedFlow).emit(
                EventData(
                    name = "notificationPosted",
                    args = listOf(event.statusBarNotification)
                )
            )
        }

        init {
            project.getLifecycle().registerObserver(object : ProjectLifecycleObserver.ExplicitObserver {
                override fun onCompileStart(project: Project) {
                    eventScope.coroutineContext.cancelChildren()
                    eventScope.cancel()
                    listeners.clear()
                }

                override fun onDestroy(project: Project) {
                    eventScope.coroutineContext.cancelChildren()
                    eventScope.cancel()
                    listeners.clear()
                }
            })
            EventHandler.on(eventScope, ::onMessageCreate)
            EventHandler.on(eventScope, ::onDebugRoomMessageCreate)
            EventHandler.on(eventScope, ::onNotificationPosted)
        }

        data class EventData(
            val name: String,
            val args: List<Any>,
        )

        data class MessageData(
            val room        : String,
            val channelId   : Long,
            val content     : String,
            val isGroupChat : Boolean,
            val isDebugRoom : Boolean,
            val image       : Image?,
            val isMention   : Boolean,
            val logId       : Long,
            val author      : Author,
            private val _reply       : (String) -> Unit,
            private val _markAsRead  : () -> Unit,
            val packageName : String,
        ) {
            companion object {
                fun fromMessage(message: Message): MessageData =
                    MessageData(
                        room        = message.room.name,
                        channelId   = message.room.id.toLongOrNull() ?: message.room.id.hashCode().toLong(),
                        content     = message.message,
                        isGroupChat = message.room.isGroupChat,
                        isDebugRoom = message.room.isDebugRoom,
                        image       = message.image?.let(::Image),
                        isMention   = message.hasMention,
                        logId       = message.chatLogId,
                        author      = Author(
                            name   = message.sender.name,
                            avatar = Image(message.sender.profileBitmap),
                            hash   = message.sender.id,
                        ),
                        _reply       = { msg -> message.room.send(msg) },
                        _markAsRead  = { message.room.markAsRead() },
                        packageName = message.packageName,
                    )
            }

            fun reply(message: String) =
                _reply(message)

            fun markAsRead() =
                _markAsRead()

            class Image(private val data: Bitmap) {

                fun getBase64(): String {
                    return data.toBase64()
                }

                fun getBitmap(): Bitmap {
                    return data
                }
            }

            data class Author(
                val name   : String,
                val avatar : Image,
                val hash   : String?,
            )
        }

        data class CommandData(
            val command     : String,
            val args        : List<String>,
            val room        : String,
            val channelId   : Long,
            val content     : String,
            val isGroupChat : Boolean,
            val isDebugRoom : Boolean,
            val image       : MessageData.Image?,
            val isMention   : Boolean,
            val logId       : Long,
            val author      : Author,
            val reply       : (String) -> Unit,
            val markAsRead  : () -> Unit,
            val packageName : String,
        ) {
            companion object {
                fun fromMessage(command: String, args: List<String>, message: Message): CommandData =
                    CommandData(
                        command     = command,
                        args        = args,
                        room        = message.room.name,
                        channelId   = message.room.id.toLongOrNull() ?: message.room.id.hashCode().toLong(),
                        content     = message.message,
                        isGroupChat = message.room.isGroupChat,
                        isDebugRoom = message.room.isDebugRoom,
                        image       = message.image?.let(MessageData::Image),
                        isMention   = message.hasMention,
                        logId       = message.chatLogId,
                        author      = Author(
                            name   = message.sender.name,
                            avatar = MessageData.Image(message.sender.profileBitmap),
                            hash   = message.sender.id,
                        ),
                        reply       = { msg -> message.room.send(msg) },
                        markAsRead  = { message.room.markAsRead() },
                        packageName = message.packageName,
                    )
            }

        }
    }
}