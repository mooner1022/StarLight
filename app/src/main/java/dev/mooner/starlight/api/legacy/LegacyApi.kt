/*
 * LegacyApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.R
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.api.msgbot.ScriptVersion
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.languages.neonjs.compileForScript
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.listener.chat.ChatSessions
import dev.mooner.starlight.listener.legacy.DebugRoomLinks
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.script.runCallback
import dev.mooner.starlight.utils.canPostNotification
import dev.mooner.starlight.utils.createNotificationChannel
import java.math.BigInteger
import java.util.concurrent.Callable
import kotlin.random.Random

private val LOG = LoggerFactory.logger {  }

private const val NOTI_CHANNEL_ID = "starlight_script_notification"

@Suppress("unused")
class LegacyApi: Api<LegacyApi.Api>() {

    override val name: String = "Api"

    override val objects: List<ApiObject> =
        getApiObjects<Api>()

    override val instanceClass: Class<Api> =
        Api::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Api(project)

    /**
     * The `Api` global of legacy projects: MessengerBot's controls for projects, rooms, toasts and notifications.
     *
     * A script name here is a StarLight project's name, matched exactly; a `.js` at the end is dropped first, and a
     * number is taken as its text.
     *
     * Rooms are reached through the messages StarLight has received since it started, from every messenger it reads,
     * and through rooms a script bound with `sessionManager.bindSession`. A `room` argument is a name (a string, even
     * one of digits), an ID (a `BigInt` or a whole number) or a room object. Sending to a room answers `true` once
     * the room has a reply session, whether or not the messenger took the text, and shows no toast.
     *
     * MessengerBot's `papagoTranslate`, `getRoomList`, `getLastImage` and `getLastImageBitmap` don't exist here.
     *
     * @see guide:projects#api-levels
     * @see guide:testing#how-debug-messages-differ
     */
    class Api(
        private val project: Project
    ) {

        companion object {
            const val STATE_PROJECT_NOT_FOUND = 0
            const val STATE_COMPILE_SUCCESS   = 1
            const val STATE_ALREADY_COMPILED  = 2
        }

        /**
         * The app's Android `Context`: the application context, not an activity.
         */
        fun getContext(): Context {
            return GlobalApplication.requireContext()
        }

        /**
         * Compiles every project that isn't compiling right now, waits until that is done and returns `true`.
         *
         * Every project is compiled, whatever its language and whether it is on, the calling one included unless it is
         * in the middle of compiling. Compiling a project that is already compiled stops its running jobs first; a failed
         * compile is logged in the project's log and stops the project until it compiles again, and doesn't change the
         * result.
         *
         * @return Always `true`.
         */
        fun reload(): Boolean =
            reloadAll()

        /**
         * Compiles the project named [scriptName] and says whether it compiled; `null` compiles every project, as the
         * form without arguments does.
         *
         * Compiling a project that is already compiled stops its running jobs first, so this applies to the calling
         * project too. A failed compile is logged in the project's log and returns `false`; it is never thrown.
         *
         * @param scriptName The project's name, with or without `.js`; `null` or `undefined` for all projects.
         * @return `false` if no project has that name, or if it didn't compile. `true` for all projects.
         */
        fun reload(scriptName: Any?): Boolean =
            if (ScriptArgs.isMissing(scriptName)) reloadAll() else reloadNamed(scriptName)

        /**
         * Compiles the project named [scriptName] and says whether it compiled; [throwOnError] is accepted and doesn't
         * make a failed compile throw.
         *
         * @param scriptName The project's name, with or without `.js`; `null` or `undefined` for all projects.
         * @param throwOnError Not used: a compile error is logged in the project's log and the result is `false`.
         * @return `false` if no project has that name, or if it didn't compile. `true` for all projects.
         */
        fun reload(scriptName: Any?, throwOnError: Any?): Boolean =
            reload(scriptName)

        /**
         * Compiles every project, as [reload] does.
         *
         * @return Always `true`.
         */
        fun compile(): Boolean =
            reloadAll()

        /**
         * Compiles the project named [scriptName], as [reload] does.
         *
         * @param scriptName The project's name, with or without `.js`; `null` for all projects.
         * @return `false` if no project has that name, or if it didn't compile.
         */
        fun compile(scriptName: Any?): Boolean =
            reload(scriptName)

        /**
         * Compiles the project named [scriptName], as [reload] does.
         *
         * @param scriptName The project's name, with or without `.js`; `null` for all projects.
         * @param throwOnError Not used: a compile error is never thrown.
         * @return `false` if no project has that name, or if it didn't compile.
         */
        fun compile(scriptName: Any?, throwOnError: Any?): Boolean =
            reload(scriptName)

        /**
         * Compiles every project that is neither compiled nor compiling, and says how many it compiled.
         *
         * A project counts whether or not its compile succeeded.
         *
         * @return The number of projects it tried to compile.
         */
        fun prepare(): Int {
            var count = 0
            for (mProject in projectManager.getProjects()) {
                if (mProject.isCompiled || isCompilingNow(mProject))
                    continue
                mProject.compileForScript(throwException = false)
                count++
            }
            return count
        }

        /**
         * Compiles the project named [scriptName] unless it is compiled already.
         *
         * `undefined` names the calling project.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `0` if no project has that name or it didn't compile, `1` if it compiled now, `2` if it was compiled
         * already. A compile error is logged, not thrown.
         */
        fun prepare(scriptName: Any?): Int {
            val target = findProject(selfOrName(scriptName)) ?: return STATE_PROJECT_NOT_FOUND
            if (target.isCompiled)
                return STATE_ALREADY_COMPILED
            return if (target.compileForScript(throwException = false)) STATE_COMPILE_SUCCESS else STATE_PROJECT_NOT_FOUND
        }

        /**
         * Compiles the project named [scriptName] unless it is compiled already; [throwOnError] is accepted and not
         * used.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @param throwOnError Not used: a compile error is never thrown.
         * @return `0` if no project has that name or it didn't compile, `1` if it compiled now, `2` if it was compiled
         * already.
         */
        fun prepare(scriptName: Any?, throwOnError: Any?): Int =
            prepare(scriptName)

        /**
         * Unloads the project named [scriptName]: stops its jobs, drops its compiled script and turns it off.
         *
         * Unlike MessengerBot, the project is turned off as well, so it stays unloaded until it is compiled and turned
         * on again.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `false` if no project has that name, it isn't compiled, or [scriptName] is `null`.
         */
        fun unload(scriptName: Any?): Boolean {
            if (ScriptArgs.isMissing(scriptName))
                return false
            val target = findProject(nameOf(scriptName)) ?: return false
            if (!target.isCompiled)
                return false
            target.destroy(requestUpdate = true)
            return true
        }

        /**
         * Unloads nothing and returns `false`: unloading needs a project's name.
         *
         * @return Always `false`.
         */
        fun unload(): Boolean =
            false

        /**
         * Turns off every project that is on, the calling one included.
         *
         * To turn off one project, pass its name.
         *
         * @return Always `true`.
         */
        fun off(): Boolean {
            for (mProject in projectManager.getEnabledProjects())
                mProject.setEnabled(false)
            return true
        }

        /**
         * Turns off the project named [scriptName]; `null` turns off every project, as `off()` does.
         *
         * A project that isn't compiled is left as it is, and this still returns `true`.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `false` if no project has that name, `true` otherwise.
         */
        fun off(scriptName: Any?): Boolean {
            if (ScriptArgs.isMissing(scriptName))
                return off()
            val target = findProject(nameOf(scriptName)) ?: return false
            target.setEnabled(false)
            return true
        }

        /**
         * Turns on every compiled project.
         *
         * A project that isn't compiled stays off; compile it first with [reload].
         *
         * @return Always `true`.
         */
        fun on(): Boolean {
            for (mProject in projectManager.getProjects())
                mProject.setEnabled(true)
            return true
        }

        /**
         * Turns on the project named [scriptName], if it is compiled; `null` turns on every project, as `on()` does.
         *
         * A project that isn't compiled stays off, and this still returns `true`: compile it first with [reload] or
         * [prepare].
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `false` if no project has that name, `true` otherwise.
         */
        fun on(scriptName: Any?): Boolean {
            if (ScriptArgs.isMissing(scriptName))
                return on()
            val target = findProject(nameOf(scriptName)) ?: return false
            target.setEnabled(true)
            return true
        }

        /**
         * Whether the calling project is on.
         */
        fun isOn(): Boolean =
            project.info.isEnabled

        /**
         * Whether the project named [scriptName] is on.
         *
         * `undefined` names the calling project.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `false` also when no project has that name.
         */
        fun isOn(scriptName: Any?): Boolean =
            findProject(selfOrName(scriptName))?.info?.isEnabled ?: false

        /**
         * Whether the calling project is compiled.
         */
        fun isCompiled(): Boolean =
            project.isCompiled

        /**
         * Whether the project named [scriptName] is compiled.
         *
         * `undefined` names the calling project.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `false` also when no project has that name.
         */
        fun isCompiled(scriptName: Any?): Boolean =
            findProject(selfOrName(scriptName))?.isCompiled ?: false

        /**
         * Whether any project is compiling right now, its compile button's or a script's compile.
         *
         * A project's own top-level code runs while it compiles, so this is `true` when a script asks from there.
         */
        fun isCompiling(): Boolean =
            projectManager.getProjects().any(::isCompilingNow)

        /**
         * Whether the project named [scriptName] is compiling right now; `null` asks about every project.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `false` also when no project has that name.
         */
        fun isCompiling(scriptName: Any?): Boolean {
            if (ScriptArgs.isMissing(scriptName))
                return isCompiling()
            val target = findProject(nameOf(scriptName)) ?: return false
            return isCompilingNow(target)
        }

        /**
         * The names of all projects, whatever their language and state.
         */
        fun getScriptNames(): Array<String> {
            return projectManager.getProjects().map { it.info.name }.toTypedArray()
        }

        /**
         * Sends [message] to [room] and says whether the room has a reply session.
         *
         * `room` is a name, an ID or a room object; see this API's description. When it names or numbers the project's
         * open debug room, the message shows there as a bot message and `true` is returned. The text of `message` is
         * made as `String(value)` would for a number or `null`.
         *
         * `true` means the room has a session, not that the messenger took the text. No toast is shown.
         *
         * @param room The room's name, ID or object.
         * @param message The text to send.
         * @return `false` if [room] is `null` or no known room matches.
         * @throws IllegalArgumentException `"hideToast must be a boolean"` when it is an object.
         */
        fun replyRoom(room: Any?, message: Any?): Boolean =
            replyRoom(room, message, null)

        /**
         * Sends [message] to [room] as [replyRoom] does; [hideToast] is accepted and ignored.
         *
         * @param room The room's name, ID or object.
         * @param message The text to send.
         * @param hideToast Not used; no toast is ever shown.
         * @return `false` if [room] is `null` or no known room matches.
         * @throws IllegalArgumentException `"hideToast must be a boolean"` when it is an object.
         */
        fun replyRoom(room: Any?, message: Any?, hideToast: Any?): Boolean {
            val text = ScriptArgs.stringify(message)
            ScriptArgs.boolean(hideToast, "hideToast", false)
            if (ScriptArgs.isNullish(room))
                return false
            if (DebugRoomLinks.send(project.info.id, room, text))
                return true
            return ChatSessions.replyTo(room, text)
        }

        /**
         * Sends [message] to [room] as Markdown and says whether the share was launched.
         *
         * The room's latest messenger gets a share marked as Markdown. KakaoTalk currently may refuse it, so `true`
         * only means the share was launched. A debug room is written to as with [replyRoom].
         *
         * @param room The room's name, ID or object.
         * @param message The text to send.
         * @return `false` if [room] is `null`, no known room matches, or nothing can take the share.
         */
        fun replyRoomWithMarkdown(room: Any?, message: Any?): Boolean =
            replyRoomWithMarkdown(room, message, null)

        /**
         * Sends [message] to [room] as Markdown, as [replyRoomWithMarkdown] does; [hideToast] is ignored.
         *
         * @param room The room's name, ID or object.
         * @param message The text to send.
         * @param hideToast Not used.
         */
        fun replyRoomWithMarkdown(room: Any?, message: Any?, hideToast: Any?): Boolean {
            val text = ScriptArgs.stringify(message)
            ScriptArgs.boolean(hideToast, "hideToast", false)
            if (ScriptArgs.isNullish(room))
                return false
            if (DebugRoomLinks.send(project.info.id, room, text))
                return true
            return ChatSessions.markdownTo(room, text)
        }

        /**
         * Sends [message] to the room with the ID [roomID] and says whether the room has a reply session.
         *
         * [roomID] is a string of digits, a `BigInt` or a whole number; keep a large ID as a string or `BigInt`, since a
         * number can't hold it exactly. The debug room isn't reachable by ID here. No toast is shown.
         *
         * @param roomID The room's ID.
         * @param message The text to send.
         * @return `false` if no known room has that ID.
         * @throws IllegalArgumentException `"roomID must be String or BigInt"` for any other kind of value, `null`
         * included.
         * @throws NumberFormatException When [roomID] is a string that isn't a number.
         */
        fun replyToID(roomID: Any?, message: Any?): Boolean =
            replyToID(roomID, message, null)

        /**
         * Sends [message] to the room with the ID [roomID] as [replyToID] does; [hideToast] is accepted and ignored.
         *
         * @param roomID The room's ID.
         * @param message The text to send.
         * @param hideToast Not used.
         * @return `false` if no known room has that ID.
         * @throws IllegalArgumentException `"roomID must be String or BigInt"` for a value that isn't an ID.
         */
        fun replyToID(roomID: Any?, message: Any?, hideToast: Any?): Boolean {
            val id = idOf(roomID)
            val text = ScriptArgs.stringify(message)
            ScriptArgs.boolean(hideToast, "hideToast", false)
            val key = ChatSessions.registry.byId(id) ?: return false
            return ChatSessions.reply(key, text)
        }

        /**
         * Sends [message] as Markdown to the room with the ID [roomID], as [replyRoomWithMarkdown] does for a name.
         *
         * @param roomID The room's ID, as for [replyToID].
         * @param message The text to send.
         * @return `false` if no known room has that ID, or nothing can take the share.
         * @throws IllegalArgumentException `"roomID must be String or BigInt"` for a value that isn't an ID.
         */
        fun replyToIDWithMarkdown(roomID: Any?, message: Any?): Boolean =
            replyToIDWithMarkdown(roomID, message, null)

        /**
         * Sends [message] as Markdown to the room with the ID [roomID]; [hideToast] is ignored.
         *
         * @param roomID The room's ID, as for [replyToID].
         * @param message The text to send.
         * @param hideToast Not used.
         */
        fun replyToIDWithMarkdown(roomID: Any?, message: Any?, hideToast: Any?): Boolean {
            val id = idOf(roomID)
            val text = ScriptArgs.stringify(message)
            ScriptArgs.boolean(hideToast, "hideToast", false)
            val key = ChatSessions.registry.byId(id) ?: return false
            return ChatSessions.sendMarkdown(key, text)
        }

        /**
         * Marks the messages of the room with the ID [roomID] as read, through every messenger that has a session of it.
         *
         * @param roomID The room's ID, as for [replyToID].
         * @return `false` if no known room has that ID, or no read action could be sent.
         * @throws IllegalArgumentException `"roomID must be String or BigInt"` for a value that isn't an ID.
         */
        fun markAsReadOnID(roomID: Any?): Boolean {
            val key = ChatSessions.registry.byId(idOf(roomID)) ?: return false
            return ChatSessions.markAsRead(key)
        }

        /**
         * Whether a reply session exists for [room], so that [replyRoom] can send to it.
         *
         * A room has one once a message from it arrived since StarLight started, or a script bound one. This doesn't
         * check that sending would succeed. The debug room has none: this is `false` for its name.
         *
         * @param room The room's name, ID or object.
         * @return `false` for `null` and for an unknown room.
         */
        fun canReply(room: Any?): Boolean =
            ChatSessions.canReply(room)

        /**
         * Shows a toast with [content], as text, and returns at once.
         *
         * The toast is shown on the main thread.
         *
         * @param content The text to show; `null` shows `"null"`.
         */
        fun showToast(content: Any?) {
            showToast(content, null)
        }

        /**
         * Shows a toast with [content] for [length], and returns at once.
         *
         * @param content The text to show; `null` shows `"null"`.
         * @param length `0` (`Toast.LENGTH_SHORT`) or `1` (`Toast.LENGTH_LONG`); anything that isn't a number is `0`.
         */
        fun showToast(content: Any?, length: Any?) {
            val text = ScriptArgs.stringify(content)
            val duration = ScriptArgs.int(length, "length", 0)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(GlobalApplication.requireContext(), text, duration).show()
            }
        }

        /**
         * Posts a notification with [title] as its title and [content] as its text, and returns `true`.
         *
         * The ID is random, between 100 and 10100. The notification shows on a high-importance channel; without the
         * notification permission nothing is shown, and the result is still `true`.
         *
         * @param title The notification's title, as text.
         * @param content The notification's text, as text.
         */
        fun makeNoti(title: Any?, content: Any?): Boolean =
            makeNoti(title, content, null)

        /**
         * Posts a notification with [title], [content] and the ID [id], replacing one with the same ID, and returns
         * `true`.
         *
         * @param title The notification's title, as text.
         * @param content The notification's text, as text.
         * @param id The notification's ID: a number, or a string of digits; anything else gives a random one between 100
         * and 10100.
         * @return `true` as soon as the notification is queued; `false` only if queueing fails.
         */
        @SuppressLint("MissingPermission")
        fun makeNoti(title: Any?, content: Any?, id: Any?): Boolean {
            val titleText = ScriptArgs.stringify(title)
            val contentText = ScriptArgs.stringify(content)
            val notiId = ScriptArgs.int(id, "id", Random.nextInt(0, 10001) + 100)
            return try {
                Handler(Looper.getMainLooper()).post {
                    try {
                        val context = GlobalApplication.requireContext()
                        if (!context.canPostNotification())
                            return@post
                        context.createNotificationChannel(
                            NOTI_CHANNEL_ID, "Script notifications", null, NotificationManager.IMPORTANCE_HIGH)
                        val notification = NotificationCompat.Builder(context, NOTI_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(titleText)
                            .setContentText(contentText)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .build()
                        NotificationManagerCompat.from(context).notify(notiId, notification)
                    } catch (e: Exception) {
                        LOG.warn { "makeNoti failed: $e" }
                    }
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Asks the JVM to collect garbage now.
         *
         * It logs a warning each time: the runtime collects garbage on its own, and calling this is rarely useful.
         */
        fun gc() {
            LOG.warn {
                translate {
                    Locale.ENGLISH { "Java Virtual Machine automatically makes garbage collection call.\nPlease be sure on what you're doing." }
                    Locale.KOREAN  { "Java 가상 머신은 자동으로 가비지 컬렉션을 수행합니다.\n뭘 하고 계신건지 정확히 알고 실행해 주세요." }
                }
            }
            System.gc()
        }

        /**
         * Runs [func] on the app's main thread and returns at once, without waiting for it.
         *
         * [func] is called with no arguments. Keep it short: the app's screens don't respond while it runs. If the
         * project was compiled again before it got to run, it doesn't run. An error [func] throws is logged in the
         * project's log.
         *
         * @param func The function to run on the main thread.
         * @throws IllegalArgumentException `"function must be a function"` when [func] isn't one.
         */
        fun UIThread(func: Callable<Any?>?) {
            UIThread(func, null)
        }

        /**
         * Runs [func] on the app's main thread, then calls [onComplete] with its error or result, and returns at once.
         *
         * [func] is called with no arguments. [onComplete] is called with `(error, result)` as one of the project's
         * events, on its workers: what [func] threw and `null`, or `null` and what it returned. Neither runs if the
         * project was compiled again in the meantime. Without [onComplete], an error is logged in the project's log.
         *
         * @param func The function to run on the main thread.
         * @param onComplete Called with `(error, result)`; `null` or left out to skip it.
         * @throws IllegalArgumentException `"function must be a function"` or `"onComplete parameter must be a
         * function"` when an argument isn't a function.
         */
        fun UIThread(func: Callable<Any?>?, onComplete: ((error: Throwable?, result: Any?) -> Unit)?) {
            if (func == null)
                throw IllegalArgumentException("function must be a function")
            val version = ScriptVersion.of(project)
            Handler(Looper.getMainLooper()).post {
                if (!ScriptVersion.isCurrent(project, version))
                    return@post
                val outcome = runCatching { func.call() }
                if (onComplete == null) {
                    outcome.exceptionOrNull()?.let { error ->
                        project.logger.e("UIThread: $error")
                    }
                    return@post
                }
                val error = outcome.exceptionOrNull()
                val result = outcome.getOrNull()
                project.runCallback("Api.UIThread", { description ->
                    translate {
                        Locale.ENGLISH { "Api.UIThread's onComplete failed: $description" }
                        Locale.KOREAN  { "Api.UIThread의 onComplete에서 오류가 났어요: $description" }
                    }
                }) {
                    if (ScriptVersion.isCurrent(project, version))
                        onComplete(error, result)
                }
            }
        }

        /**
         * The number of event jobs the calling project is running right now.
         *
         * StarLight counts the project's running events, not threads as MessengerBot does.
         */
        fun getActiveThreadsCount(): Int =
            project.activeJobs()

        /**
         * The number of event jobs the project named [scriptName] is running right now.
         *
         * `undefined` names the calling project.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `0` also when no project has that name.
         */
        fun getActiveThreadsCount(scriptName: Any?): Int =
            findProject(selfOrName(scriptName))?.activeJobs() ?: 0

        /**
         * Stops the calling project's running events and drops the ones waiting, as [interruptThreads] with a name does.
         *
         * @return An array of the dropped events, or `null` when nothing was running or waiting.
         */
        fun interruptThreads(): Any? =
            interruptProject(project)

        /**
         * Stops the running events of the project named [scriptName] and drops the ones waiting.
         *
         * `undefined` names the calling project. A function that is already running is cancelled as far as the project's
         * workers allow. The next event starts new workers. A call from the project's own script may interrupt that
         * script too.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return An array of the events that never started, which may be empty, or `null` when no project has that
         * name or nothing was running or waiting.
         */
        fun interruptThreads(scriptName: Any?): Any? =
            findProject(selfOrName(scriptName))?.let(::interruptProject)

        /**
         * Whether the calling project has no event jobs running or waiting.
         *
         * Right after [interruptThreads] this is `true`, even if a function that was running hasn't returned yet.
         */
        fun isTerminated(): Boolean =
            isIdle(project)

        /**
         * Whether the project named [scriptName] has no event jobs running or waiting.
         *
         * `undefined` names the calling project. Unlike MessengerBot, an idle project that has run events counts as
         * terminated too.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return `true` also when no project has that name.
         */
        fun isTerminated(scriptName: Any?): Boolean {
            val target = findProject(selfOrName(scriptName)) ?: return true
            return isIdle(target)
        }

        /**
         * Marks the messages of the last room a message came from as read.
         *
         * This form without a room is StarLight's; MessengerBot has none.
         *
         * @return `false` if no message has arrived, or the messenger's read action failed.
         */
        fun markAsRead(): Boolean =
            NotificationListener.markAsRead()

        /**
         * Marks the messages of [room] as read, through every messenger that has a session of it.
         *
         * @param room The room's name, ID or object.
         * @return `true` when at least one read action was sent; `false` for `null` and for an unknown room.
         */
        fun markAsRead(room: Any?): Boolean =
            markAsRead(room, null)

        /**
         * Marks the messages of [room] as read through the messenger [packageName] only; `null` means every messenger.
         *
         * @param room The room's name, ID or object.
         * @param packageName The messenger's package name, such as `com.kakao.talk`.
         * @return `true` when a read action was sent; `false` when the room is unknown or has no read action there.
         */
        fun markAsRead(room: Any?, packageName: Any?): Boolean {
            if (ScriptArgs.isNullish(room))
                return false
            return ChatSessions.markRoomAsRead(room, ScriptArgs.stringOrNull(packageName))
        }

        // region helpers

        private fun nameOf(value: Any?): String =
            ScriptArgs.stringify(value).removeSuffix(".js")

        private fun findProject(name: String): Project? =
            if (name == project.info.name) project else projectManager.getProjectByName(name)

        /**
         * The name a script-name argument stands for: `undefined` is the calling project. NeonJS passes `null` for both
         * `null` and `undefined`, so there `null` is the calling project too; in Rhino `null` names no project.
         */
        private fun selfOrName(value: Any?): String = when {
            ScriptArgs.isUndefined(value) -> project.info.name
            value == null                -> if (project.getLanguage().id == "js_neon") project.info.name else ""
            ScriptArgs.isNullish(value)  -> project.info.name
            else                         -> nameOf(value)
        }

        private fun isCompilingNow(target: Project): Boolean =
            target.getLifecycle().state == ProjectLifecycle.State.COMPILING

        private fun isIdle(target: Project): Boolean =
            target.activeJobs() == 0 && target.queuedEvents() == 0

        private fun reloadAll(): Boolean {
            for (target in projectManager.getProjects()) {
                if (isCompilingNow(target))
                    continue
                target.compileForScript(throwException = false)
            }
            return true
        }

        private fun reloadNamed(scriptName: Any?): Boolean {
            val target = findProject(nameOf(scriptName)) ?: return false
            return target.compileForScript(throwException = false)
        }

        private fun interruptProject(target: Project): Any? {
            val waiting = target.queuedEvents()
            val running = target.activeJobs()
            target.stopAllJobs()
            if (waiting == 0 && running == 0)
                return null
            return Array<Runnable>(waiting) { Runnable { } }
        }

        /** A room ID as MessengerBot reads it: a string of digits, a `BigInt` or a whole number. */
        private fun idOf(value: Any?): BigInteger {
            if (value is CharSequence || (value is NeonValue && value.isString))
                return BigInteger(ScriptArgs.stringify(value))
            return ScriptArgs.integral(value)
                ?: throw IllegalArgumentException("roomID must be String or BigInt")
        }

        // endregion
    }
}
