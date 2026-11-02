/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media

import android.content.Context
import dev.mooner.starlight.media.api.MediaSharerCore
import dev.mooner.starlight.media.send.KakaoShare
import dev.mooner.starlight.media.send.MediaSendService
import dev.mooner.starlight.plugincore.chat.MediaSender
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

private val LOG = LoggerFactory.logger("MediaSharer")

/**
 * Sending images, videos and other files to chat rooms: `MediaSharer.send(...)` and `room.sendMedia(...)` in scripts,
 * [dev.mooner.starlight.plugincore.chat.ChatRoom.sendMedia] for plugins. KakaoTalk rooms get the files through
 * KakaoTalk's share screen; the debug room shows them in its chat.
 */
object MediaSharing {

    @Volatile
    var service: MediaSendService? = null
        private set

    /** `room.sendMedia` has no project: relative paths are refused and messages go to the app's log. */
    private val roomCore by lazy { MediaSharerCore(null) }

    /** Starts the send service and hands rooms their sender; called once while the app starts. */
    fun init(context: Context) {
        if (service != null)
            return
        // An exception escaping a coroutine would otherwise reach the thread's handler and take the bots down.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            LOG.error { "Unexpected error while sending files: $e" }
        })
        val service = MediaSendService(File(context.filesDir, DATA_DIR), MediaSettings::read, scope)
        // Before the service is handed out: it moves the last run's leftovers aside (small, local file work).
        service.start()
        this.service = service
        MediaSender.current = MediaSender { room, files, options -> roomCore.send(room, files, options) }

        EventHandler.on<Events.Message.Create>(scope) {
            val room = message.room
            if (message.packageName == KakaoShare.KAKAO_PACKAGE && !room.isDebugRoom)
                service.rooms.record(room.id, room.name, room.isGroupChat)
        }
        EventHandler.on<Events.Notification.Post>(scope) {
            // Which KakaoTalk a room belongs to (the owner's, or a clone such as Dual Messenger): its notification's
            // user. This comes before the message is parsed.
            val sbn = statusBarNotification
            val tag = sbn.tag
            if (sbn.packageName == KakaoShare.KAKAO_PACKAGE && tag != null)
                @Suppress("DEPRECATION")
                service.rooms.recordUser(tag, sbn.userId)
        }
    }

    private const val DATA_DIR = "media"
}
