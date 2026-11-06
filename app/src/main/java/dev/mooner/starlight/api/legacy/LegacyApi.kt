/*
 * LegacyApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.mooner.neonjs.NeonContext
import dev.mooner.starlight.R
import dev.mooner.starlight.api.original.NotificationApi
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable

private val LOG = LoggerFactory.logger {  }

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
     * A script name here is a StarLight project's name, matched exactly, with no `.js` at the end.
     *
     * Rooms are reached through the messages StarLight has received since it started: a room is known once a message
     * from it has arrived through the first notification rule, the messenger StarLight reads by default, and the debug
     * room is never among them. MessengerBot also takes a room ID where a room name goes; here only [replyToID] and
     * [markAsReadOnID] take IDs.
     *
     * MessengerBot's `papagoTranslate`, `getRoomList`, `getLastImage`, `getLastImageBitmap`, `replyRoomWithMarkdown`
     * and `replyToIDWithMarkdown` don't exist here.
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
         * Compiles every project and says whether any of them compiled.
         *
         * Every project is compiled, whatever its language and whether it is on, the calling one included. Compiling a
         * project that is already compiled stops its running jobs first; a failed compile is logged in the project's
         * log and leaves the version that was running in place.
         *
         * @param throwOnError Whether to throw the first compile error. The projects after it then aren't compiled.
         * @return `true` if at least one project compiled.
         */
        @JvmOverloads
        fun reload(throwOnError: Boolean = false): Boolean {
            var res = false
            for (project in projectManager.getProjects()) {
                val cRes = project.compile(throwOnError)
                if (!res && cRes)
                    res = true
            }
            return res
        }

        /**
         * Compiles the project named [scriptName] and says whether it compiled.
         *
         * Compiling a project that is already compiled stops its running jobs first, so this applies to the calling
         * project too. A failed compile is logged in the project's log and leaves the version that was running in
         * place.
         *
         * @param scriptName The project's name, matched exactly.
         * @param throwOnError Whether to throw the compile error instead of returning `false`.
         * @return `false` if no project has that name, or if it didn't compile.
         */
        @JvmOverloads
        fun reload(scriptName: String, throwOnError: Boolean = false): Boolean {
            val mProject = if (scriptName == project.info.name)
                project
            else
                projectManager.getProjectByName(scriptName)?: return false
            return mProject.compile(throwException = throwOnError)
        }

        /**
         * Compiles every project and says whether any of them compiled; the same as [reload].
         *
         * Every project is compiled, the calling one included. Compiling a project that is already compiled stops its
         * running jobs first.
         *
         * @param throwOnError Whether to throw the first compile error. The projects after it then aren't compiled.
         * @return `true` if at least one project compiled.
         */
        @JvmOverloads
        fun compile(throwOnError: Boolean = false): Boolean =
            reload(throwOnError)

        /**
         * Compiles the project named [scriptName] and says whether it compiled; the same as [reload].
         *
         * @param scriptName The project's name, matched exactly.
         * @param throwOnError Whether to throw the compile error instead of returning `false`.
         * @return `false` if no project has that name, or if it didn't compile.
         */
        @JvmOverloads
        fun compile(scriptName: String, throwOnError: Boolean = false): Boolean =
            reload(scriptName, throwOnError)

        /**
         * Compiles the project named [scriptName] unless it is compiled already.
         *
         * Unlike MessengerBot, `scriptName` is required: there is no `prepare()` for the calling project.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `0` if no project has that name, `1` if it compiled now, `2` if it was compiled already.
         * @throws Exception The compile error, when the project fails to compile.
         */
        fun prepare(scriptName: String): Int {
            val project = projectManager.getProjectByName(scriptName)
                ?: return STATE_PROJECT_NOT_FOUND
            if (project.isCompiled)
                return STATE_ALREADY_COMPILED
            project.compile(throwException = true)
            return STATE_COMPILE_SUCCESS
        }

        /**
         * Unloads the project named [scriptName]: stops its jobs, drops its compiled script and turns it off.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `false` if no project has that name or it isn't compiled.
         */
        fun unload(scriptName: String): Boolean {
            val project = projectManager.getProjectByName(scriptName)
                ?: return false
            if (!project.isCompiled)
                return false
            project.destroy(requestUpdate = true)
            return true
        }

        /**
         * Turns off every project that is on, the calling one included.
         *
         * This differs from MessengerBot, where `Api.off()` turns off only the calling script. To turn off one
         * project, pass its name.
         *
         * @return Always `true`.
         */
        fun off(): Boolean {
            for (project in projectManager.getEnabledProjects())
                project.setEnabled(false)
            return true
        }

        /**
         * Turns off the project named [scriptName].
         *
         * A project that isn't compiled is left as it is, and this still returns `true`.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `false` if no project has that name, `true` otherwise.
         */
        fun off(scriptName: String): Boolean {
            val project = projectManager.getProjectByName(scriptName)
                ?: return false
            project.setEnabled(false)
            return true
        }

        /**
         * Turns on every compiled project.
         *
         * This differs from MessengerBot, where `Api.on()` turns on only the calling script. A project that isn't
         * compiled stays off; compile it first with [reload].
         *
         * @return Always `true`.
         */
        fun on(): Boolean {
            for (project in projectManager.getProjects())
                project.setEnabled(true)
            return true
        }

        /**
         * Turns on the project named [scriptName], if it is compiled.
         *
         * A project that isn't compiled stays off, and this still returns `true`: compile it first with [reload] or
         * [prepare].
         *
         * @param scriptName The project's name, matched exactly.
         * @return `false` if no project has that name, `true` otherwise.
         */
        fun on(scriptName: String): Boolean {
            val project = projectManager.getProjectByName(scriptName)
                ?: return false
            project.setEnabled(true)
            return true
        }

        /**
         * Whether the project named [scriptName] is on.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `false` also when no project has that name.
         */
        fun isOn(scriptName: String): Boolean {
            val project = projectManager.getProjectByName(scriptName)
                ?: return false
            return project.info.isEnabled
        }

        /**
         * Whether the project named [scriptName] is compiled.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `false` also when no project has that name.
         */
        fun isCompiled(scriptName: String): Boolean {
            val project = projectManager.getProjectByName(scriptName)
                ?: return false
            return project.isCompiled
        }

        /**
         * Whether the project named [scriptName] is compiling; always `false`, as StarLight doesn't track this yet.
         *
         * @param scriptName Not used.
         */
        fun isCompiling(scriptName: String): Boolean {
            /* TODO implement feature */
            return false
        }

        /**
         * The names of all projects, whatever their language and state.
         */
        fun getScriptNames(): Array<String> {
            return projectManager.getProjects().map { it.info.name }.toTypedArray()
        }


        /**
         * Sends [message] to the room named [room] and says whether it was sent.
         *
         * The room must have sent a message since StarLight started. It never reaches the debug room: a script tested
         * there sends to a real room with that name, if one is known. Unlike MessengerBot, `room` is a name only; send
         * by ID with [replyToID].
         *
         * On failure, unless [hideToast] is `true`, a toast says so and a warning is logged.
         *
         * @param room The room's name.
         * @param message The text to send.
         * @param hideToast Whether to skip the toast and the warning on failure.
         * @return `false` if no known room has that name, or if the messenger's reply action failed.
         */
        @JvmOverloads
        fun replyRoom(room: String, message: String, hideToast: Boolean = false): Boolean {
            val result = NotificationListener.sendTo(room, message)
            if (!hideToast && !result) {
                val context = GlobalApplication.requireContext()
                val content = context
                    .getString(R.string.api_cannot_send_to_room)
                    .format(room)

                showToast(content, Toast.LENGTH_LONG)
                LOG.warn { content }
            }
            return result
        }

        /**
         * Sends [message] to the room with the ID [roomID] and says whether it was sent.
         *
         * The room must have sent a message since StarLight started; the debug room's ID never matches. On failure,
         * unless [hideToast] is `true`, a toast says so and a warning is logged.
         *
         * @param roomID The room's ID, as a string.
         * @param message The text to send.
         * @param hideToast Whether to skip the toast and the warning on failure.
         * @return `false` if no known room has that ID, or if the messenger's reply action failed.
         */
        @JvmOverloads
        fun replyToID(roomID: String, message: String, hideToast: Boolean = false): Boolean {
            val result = NotificationListener.sendToID(roomID, message)
            if (!hideToast && !result) {
                val context = GlobalApplication.requireContext()
                val content = context
                    .getString(R.string.api_cannot_send_to_room)
                    .format(roomID)

                showToast(content, Toast.LENGTH_LONG)
                LOG.warn { content }
            }
            return result
        }

        /**
         * Sends [message] to the room with the numeric ID [roomID]; the same as passing the ID as a string.
         *
         * A room ID can be too large for a JavaScript number to hold exactly, so prefer the string form.
         *
         * @param roomID The room's ID.
         * @param message The text to send.
         * @param hideToast Whether to skip the toast and the warning on failure.
         * @return `false` if no known room has that ID, or if the messenger's reply action failed.
         */
        @JvmOverloads
        fun replyToID(roomID: Long, message: String, hideToast: Boolean = false): Boolean =
            replyToID(roomID.toString(), message, hideToast)

        /**
         * Marks the messages of the room with the ID [roomID] as read.
         *
         * The room must have sent a message since StarLight started.
         *
         * @param roomID The room's ID, as a string.
         * @return `false` if no known room has that ID, or if the messenger's read action failed.
         */
        fun markAsReadOnID(roomID: String): Boolean =
            NotificationListener.markAsReadByID(roomID)

        /**
         * Marks the messages of the room with the numeric ID [roomID] as read; the same as passing the ID as a string.
         *
         * A room ID can be too large for a JavaScript number to hold exactly, so prefer the string form.
         *
         * @param roomID The room's ID.
         * @return `false` if no known room has that ID, or if the messenger's read action failed.
         */
        fun markAsReadOnID(roomID: Long): Boolean =
            markAsReadOnID(roomID.toString())

        /**
         * Whether a room named [room] is known, so that [replyRoom] can send to it.
         *
         * A room is known once it has sent a message since StarLight started; this doesn't check that sending would
         * succeed. Unlike MessengerBot, `room` is a name only.
         *
         * @param room The room's name.
         */
        fun canReply(room: String): Boolean {
            return NotificationListener.hasRoom(room)
        }

        /**
         * Shows a toast with [content].
         *
         * The toast is shown on the main thread; this returns at once. Unlike MessengerBot, [length] is required.
         *
         * @param content The text to show.
         * @param length `0` (`Toast.LENGTH_SHORT`) or `1` (`Toast.LENGTH_LONG`).
         */
        fun showToast(content: String, length: Int) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(GlobalApplication.requireContext(), content, length).show()
            }
        }

        /*
         * TODO: papagoTranslate?
         */

        /**
         * Posts a notification with [title] and [content], replacing the app's notification with the same [id].
         *
         * Unlike MessengerBot's, it returns nothing. Without the notification permission nothing is shown, and an error
         * is logged.
         *
         * @param title The notification's title.
         * @param content The notification's text. Android shows it as the subtext, the small text next to StarLight's
         * name at the top, not as the body.
         * @param id The notification's ID; `0` when left out.
         */
        @JvmOverloads
        fun makeNoti(title: String, content: String, id: Int? = null) {
            NotificationApi.NotificationBuilder(id ?: 0).apply {
                setTitle(title)
                setText(content)
            }.build()
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
         * Runs [func] on the app's main thread, then passes its result or error to [onComplete].
         *
         * The calling script waits until `func` has returned, and `onComplete` runs on the caller's thread before this
         * returns. Keep `func` short: the app's screens don't respond while it runs. In NeonJS, the script lets go of
         * its context while it waits, so `func` and code other threads call in the meantime (Java callbacks, other
         * workers when the project runs more than one) can run.
         *
         * @param func The function to run on the main thread.
         * @param onComplete Called with `(error, result)`: what `func` threw and `null`, or `null` and what it
         * returned.
         */
        @JvmOverloads
        fun UIThread(func: Callable<Any>, onComplete: (error: Throwable?, result: Any?) -> Unit = { _, _ -> }) {
            val outcome = runCatching {
                // On the main thread already (in a UI callback, say): waiting for it would never end.
                if (Looper.myLooper() == Looper.getMainLooper())
                    func.call()
                else
                    whileWaiting { runBlocking(Dispatchers.Main) { func.call() } }
            }
            outcome.fold({ onComplete(null, it) }, { onComplete(it, null) })
        }

        /**
         * Runs [block], which waits for the main thread, letting a NeonJS script's context go meanwhile: the main
         * thread calling `func` needs it, and would otherwise wait for this thread forever. Rhino has no such lock.
         */
        private fun <R> whileWaiting(block: () -> R): R {
            val context = NeonContext.current() ?: return block()
            return context.releasing(block)
        }

        /**
         * The number of event jobs the calling project is running right now.
         *
         * StarLight counts the project's running events, not threads as MessengerBot does.
         */
        fun getActiveThreadsCount(): Int {
            return getActiveThreadsCount(project.info.name)
        }

        /**
         * The number of event jobs the project named [scriptName] is running right now.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `0` also when no project has that name.
         */
        fun getActiveThreadsCount(scriptName: String): Int {
            val project = projectManager.getProjectByName(scriptName)
                ?: return 0
            return project.activeJobs()
        }

        /**
         * Stops the calling project's workers, dropping the events waiting to run.
         *
         * A function that is already running isn't interrupted, unlike MessengerBot's thread interrupt. The next event
         * starts new workers.
         *
         * @return Always `true`.
         */
        fun interruptThreads(): Boolean {
            return interruptThreads(project.info.name)
        }

        /**
         * Stops the workers of the project named [scriptName], dropping the events waiting to run.
         *
         * A function that is already running isn't interrupted. The next event starts new workers.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `false` if no project has that name.
         */
        fun interruptThreads(scriptName: String): Boolean {
            val project = projectManager.getProjectByName(scriptName)
                ?: return false
            project.stopAllJobs()
            return true
        }

        /**
         * Whether the calling project has no event jobs running.
         *
         * Right after [interruptThreads] this is `true`, even if a function that was running hasn't returned yet.
         */
        fun isTerminated(): Boolean {
            return isTerminated(project.info.name)
        }

        /**
         * Whether the project named [scriptName] has no event jobs running.
         *
         * Right after [interruptThreads] this is `true`, even if a function that was running hasn't returned yet.
         *
         * @param scriptName The project's name, matched exactly.
         * @return `false` also when no project has that name.
         */
        fun isTerminated(scriptName: String): Boolean {
            val project = projectManager.getProjectByName(scriptName)
                ?: return false
            return project.activeJobs() == 0
        }

        /**
         * Marks the messages of the room named [room] as read, or those of the last room a message came from.
         *
         * Unlike MessengerBot, `room` is a name only, and it can be left out.
         *
         * @param room The room's name; `null` or left out for the room of the last message received.
         * @param packageName Not used.
         * @return `false` if the room isn't known, or if the messenger's read action failed.
         */
        @JvmOverloads
        fun markAsRead(room: String? = null, packageName: String? = null): Boolean {
            return if (room == null)
                NotificationListener.markAsRead()
            else
                NotificationListener.markAsRead(room)
        }
    }
}