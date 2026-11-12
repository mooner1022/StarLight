/*
 * ChatApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.api.api2.Api2Message
import dev.mooner.starlight.api.api2.Api2Room
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.script.chat.ChatWaits
import dev.mooner.starlight.script.runCallback
import java.util.concurrent.CompletableFuture

class ChatApi: Api<ChatApi.Chat>() {

    /**
     * Chat beyond a single event: waiting for the next message as an answer, and finding the rooms the bot can send
     * to; a StarLight original global.
     *
     * ```js
     * export async function onMessage(event) {
     *     if (event.message !== "!join") return;
     *     event.room.send("What's your nickname?");
     *     const answer = await Chat.waitFor(event, 30_000);
     *     event.room.send(answer ? `Welcome, ${answer.message}!` : "Time's up.");
     * }
     * ```
     *
     * @see guide:events#waiting-for-an-answer
     */
    class Chat internal constructor(
        private val project: Project,
    ) {

        /**
         * Waits up to a minute for the next message [target] names, and resolves to it, or to `null` when none comes.
         *
         * The message that answers goes to the waiting script only: this project's `onMessage`, `response` and
         * API2 listeners don't get it. Other projects get it as usual. See the other forms for the details.
         *
         * @param target A message, as `event` or an API2 `msg`: the next message from the same sender in the same
         * room. A room, as `event.room` or one from `Chat.findRoom`: the next message from anyone in it.
         * @return A Promise in NeonJS; a `CompletableFuture` in Rhino, whose `join()` waits for it.
         * @throws IllegalArgumentException When [target] is neither a message nor a room.
         */
        fun waitFor(target: Any): CompletableFuture<Message?> =
            waitFor(target, null)

        /**
         * Waits for the next message [target] names, as [options] say, and resolves to it, or to `null` when none
         * comes in time.
         *
         * - The message that answers goes to the waiting script only: this project's `onMessage`, `response` and
         *   API2 listeners don't get it. Other projects get it as usual.
         * - The message that started the wait never answers it, nor does one that came before it and was handled
         *   already: the wait takes the next one this project handles.
         * - A new wait for the same target, the same sender in the same room or the same room, ends the earlier one
         *   with `null`, as when someone sends the same command twice. A message answers every wait it matches.
         * - Compiling the project again or unloading it ends its waits without resolving them.
         * - A sender is told apart by their ID; where the messenger gives none, as in the debug room, by their name.
         * - In Rhino, `join()` keeps one of the project's workers waiting: pass a callback instead.
         *
         * @param target A message, as `event` or an API2 `msg`: the next message from the same sender in the same
         * room. A room, as `event.room` or one from `Chat.findRoom`: the next message from anyone in it.
         * @param options How long to wait, in milliseconds, as a number or as `{ timeout }`: from 1 to 600000 (10
         * minutes); 60000 when `null`.
         * @return A Promise in NeonJS; a `CompletableFuture` in Rhino, whose `join()` waits for it.
         * @throws IllegalArgumentException When [target] is neither a message nor a room, or the timeout is out of
         * range.
         */
        fun waitFor(target: Any, options: Any?): CompletableFuture<Message?> {
            val future = CompletableFuture<Message?>()
            ChatWaits.start(project.info.id, targetOf(target), timeoutOf(options),
                onDone = { answer -> future.complete(answer) },
                // Cancelled once the version that waited can no longer act on it: a Rhino worker in join() ends.
                onRelease = { future.cancel(false) },
            )
            return future
        }

        /**
         * Waits for the next message [target] names, as [options] say, and calls [callback] with it, or with `null`
         * when none comes in time; the form for Rhino, which has no `await`.
         *
         * [callback] runs as one of the project's tasks. An error it throws is logged, and doesn't unload the
         * project. It isn't called after the project compiles again or is unloaded. The rest is as in the form that
         * returns a Promise.
         *
         * ```js
         * Chat.waitFor(event, 30000, function (answer) {
         *     event.room.send(answer ? "Got " + answer.message : "Time's up.");
         * });
         * ```
         *
         * @param target A message: the next message from the same sender in the same room. A room: the next message
         * from anyone in it.
         * @param options How long to wait, in milliseconds, as a number or as `{ timeout }`: from 1 to 600000 (10
         * minutes); 60000 when `null`.
         * @param callback Given the message that answered, or `null`.
         * @throws IllegalArgumentException When [target] is neither a message nor a room, or the timeout is out of
         * range.
         */
        fun waitFor(target: Any, options: Any?, callback: (answer: Message?) -> Unit) {
            ChatWaits.start(project.info.id, targetOf(target), timeoutOf(options),
                onDone = { answer ->
                    project.runCallback("Chat.waitFor", { error ->
                        translate {
                            Locale.ENGLISH { "Chat.waitFor's callback failed: $error" }
                            Locale.KOREAN  { "Chat.waitFor의 콜백에서 오류가 났어요: $error" }
                        }
                    }) { callback(answer) }
                },
                onRelease = {},
            )
        }

        /**
         * The room named [nameOrId], or with that ID, among those a message came from since StarLight started, or
         * whose message notification was still shown when it started; `null` when there is none.
         *
         * Sending works only to a room whose notification the bot has received since StarLight started, or that was
         * still shown when it started, as replying to a notification is how it sends.
         *
         * @param nameOrId A room's name, or its ID as text.
         */
        fun findRoom(nameOrId: String): ChatRoom? =
            NotificationListener.getRoom(nameOrId)

        /**
         * The rooms messages came from since StarLight started, or whose message notifications were still shown when it
         * started, which the bot can send to.
         */
        fun getRooms(): Array<ChatRoom> =
            NotificationListener.getRooms().toTypedArray()

        private fun targetOf(target: Any): ChatWaits.Target =
            when (target) {
                is Message     -> ChatWaits.Target(target.room.id, target.room.name, ChatWaits.senderOf(target))
                is Api2Message -> ChatWaits.Target(target.channelId?.toString(), target.room,
                    target.author.hash?.let { "id:$it" } ?: "name:${target.author.name}")
                is ChatRoom    -> ChatWaits.Target(target.id, target.name, null)
                is Api2Room    -> ChatWaits.Target(target.channelId?.toString(), target.name, null)
                else           -> throw IllegalArgumentException(
                    "Chat.waitFor takes a message, such as event, or a room, such as event.room")
            }

        private fun timeoutOf(options: Any?): Long {
            val value = when {
                ScriptValues.isAbsent(options) -> return DEFAULT_TIMEOUT_MILLIS
                ScriptValues.isObject(options) -> ScriptValues.member(options, "timeout")
                    ?: return DEFAULT_TIMEOUT_MILLIS
                else                           -> options
            }
            val millis = ScriptValues.toLong(value)
                ?: throw IllegalArgumentException("Chat.waitFor's timeout must be a number of milliseconds")
            require(millis in 1..MAX_TIMEOUT_MILLIS) {
                "Chat.waitFor's timeout must be from 1 to $MAX_TIMEOUT_MILLIS milliseconds, not $millis"
            }
            return millis
        }

        private val observer = object : ProjectLifecycleObserver.ExplicitObserver {
            /** This version's waits, taken away when the next compile started. */
            private var retired: List<ChatWaits.Wait>? = null

            override fun onCompileStart(project: Project) {
                retired = ChatWaits.detach(project.info.id)
            }

            // Released only now: the old version's code is gone, so a Rhino join() that ends can't unload the project
            // that is running, and a NeonJS promise can't run in it.
            override fun onCompileEnd(project: Project) {
                val waits = retired
                    ?: return
                retired = null
                ChatWaits.release(waits)
                project.getLifecycle().unregisterObserver(this)
            }

            override fun onDestroy(project: Project) {
                ChatWaits.release(ChatWaits.detach(project.info.id) + retired.orEmpty())
                retired = null
                project.getLifecycle().unregisterObserver(this)
            }
        }

        init {
            project.getLifecycle().registerObserver(observer)
        }

        private companion object {
            const val DEFAULT_TIMEOUT_MILLIS = 60_000L
            const val MAX_TIMEOUT_MILLIS = 600_000L
        }
    }

    override val name: String = "Chat"

    override val objects: List<ApiObject> =
        getApiObjects<Chat>()

    override val instanceClass: Class<Chat> =
        Chat::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Chat(project)
}
