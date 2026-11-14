/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.api

import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.media.MediaSharing
import dev.mooner.starlight.media.media.MediaSources
import dev.mooner.starlight.media.room.DirectRoom
import dev.mooner.starlight.media.room.RoomRegistry
import dev.mooner.starlight.media.room.directRoomOf
import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.media.script.SendOptions
import dev.mooner.starlight.media.script.TextOptions
import dev.mooner.starlight.media.send.KakaoShare
import dev.mooner.starlight.media.send.LeaveTiming
import dev.mooner.starlight.media.send.LeaveTo
import dev.mooner.starlight.media.send.MediaSendService
import dev.mooner.starlight.media.send.SendException
import dev.mooner.starlight.media.send.SendLog
import dev.mooner.starlight.media.send.SendRequest
import dev.mooner.starlight.media.utils.tr
import dev.mooner.starlight.plugincore.chat.MediaSendResult
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

private val LOG = LoggerFactory.logger("MediaSharer")

/**
 * What [MediaSharer] does for one project, or for `room.sendMedia` when [project] is null. Kept apart from it because
 * StarLight lists every member of an API class, private ones included, as the API's functions in the editor.
 *
 * Without a project, relative paths have nothing to be relative to, and messages go to the app's log.
 */
internal class MediaSharerCore(private val project: Project?) {

    @Volatile
    private var hasWarnedOverlay = false

    private val log = object : SendLog {
        override val projectName: String
            get() = project?.info?.name ?: "-"

        override fun info(message: String) {
            if (project != null) project.logger.info("$TAG $message") else LOG.info { message }
        }

        override fun warn(message: String) {
            if (project != null) project.logger.warn("$TAG $message") else LOG.warn { message }
        }
    }

    fun send(room: Any?, files: Any?, options: Any?): CompletableFuture<MediaSendResult> {
        // Script values are read here, on the script's thread; the send runs on the app's.
        val request = try {
            readRequest(room, files, options)
        } catch (e: Throwable) {
            return CompletableFuture.completedFuture(failure(labelOf(room), e))
        }
        val label = request.target.label
        val sending = try {
            service().send(request, log)
        } catch (e: Throwable) {
            return CompletableFuture.completedFuture(failure(label, e))
        }
        return sending.handle { outcome, error ->
            if (error != null)
                failure(label, (error as? CompletionException)?.cause ?: error)
            else
                MediaSendResult(true, label, outcome.sent, outcome.failures.size, null, outcome.failures, outcome.via)
        }
    }

    /** Sends [text] to [room], or to the memo chat (나와의 채팅) when [toMemo], as [options] say. */
    fun sendText(room: Any?, text: Any?, options: Any?, toMemo: Boolean): CompletableFuture<MediaSendResult> {
        val label = if (toMemo) MEMO_CHAT_LABEL else labelOf(room)
        val sending = try {
            val content = ScriptValues.toText(text)
                ?: throw IllegalArgumentException("text must be text")
            val options = TextOptions.read(options)
            val attachment = options.attachmentFor(content).toJson()
            val service = service()
            val target = if (toMemo) null else directRoomOf(room) ?: service.resolver.resolve(room)
            if (target !is DirectRoom)
                warnIfNoOverlayPermission()
            service.sendText(target, content, attachment, options.leaveTo, log)
        } catch (e: Throwable) {
            return CompletableFuture.completedFuture(failure(label, e))
        }
        return sending.handle { outcome, error ->
            if (error != null)
                failure(label, (error as? CompletionException)?.cause ?: error)
            else
                MediaSendResult(true, label, outcome.sent, 0, null, emptyList(), outcome.via)
        }
    }

    /** [to]: `"home"` (default), `"starlight"` or a package name, or a delay alone. */
    fun leave(to: Any?, delay: Any?): Boolean {
        // leave(1000): a delay alone.
        val (to, delay) = if (ScriptValues.isAbsent(delay) && ScriptValues.unwrap(to) is Number) null to to else to to delay
        return catching(tr("Failed to leave KakaoTalk", "카카오톡에서 나가지 못했어요")) {
            val leaveTo = SendOptions.leaveToOf(to) ?: LeaveTo.Home
            // No delay: as soon as KakaoTalk has read the files of the latest send.
            val timing = ScriptValues.toLong(delay)?.takeIf { it >= 0 }?.let(LeaveTiming::After) ?: LeaveTiming.AfterRead
            service().leaveKakao(leaveTo, timing)
            true
        } ?: false
    }

    fun clearCache(target: Any?): Int =
        catching(tr("Failed to clear the cache", "캐시를 비우지 못했어요")) {
            val cache = service().cache
            if (ScriptValues.isAbsent(target))
                cache.clear()
            else
                (ScriptValues.asList(target) ?: listOf(target)).sumOf { item -> ScriptValues.toText(item)?.let(cache::clear) ?: 0 }
        } ?: 0

    fun rooms(): List<RoomInfo> =
        MediaSharing.service?.rooms?.list().orEmpty().map(::roomInfo)

    fun findRoom(name: Any?): RoomInfo? =
        ScriptValues.toText(name)?.let { MediaSharing.service?.rooms?.findByName(it) }?.let(::roomInfo)

    fun hasOverlayPermission(): Boolean =
        KakaoShare.canDrawOverlays(GlobalApplication.requireContext())

    private fun readRequest(room: Any?, files: Any?, options: Any?): SendRequest {
        val service = service()
        // The debug room shows the files itself; any other room is KakaoTalk's.
        val target = directRoomOf(room) ?: service.resolver.resolve(room)
        val sources = MediaSources.parseAll(files, project?.directory)
        val options = SendOptions.read(options)
        if (target !is DirectRoom)
            warnIfNoOverlayPermission()
        return SendRequest(target, sources, options.fileName, options.timeoutMillis, options.cache, options.headers, options.leaveTo)
    }

    private fun failure(room: String, e: Throwable): MediaSendResult {
        val message = e.message ?: e.toString()
        val text = tr("Failed to send to $room", "$room 에 전송하지 못했어요") + ": $message"
        if (project != null) project.logger.error("$TAG $text") else LOG.error { text }
        return MediaSendResult.failure(room, message)
    }

    private fun warnIfNoOverlayPermission() {
        if (hasWarnedOverlay)
            return
        if (!KakaoShare.canDrawOverlays(GlobalApplication.requireContext())) {
            hasWarnedOverlay = true
            log.warn(
                tr(
                    "StarLight can't draw over other apps: files are only sent while StarLight is on screen. " +
                        "Allow it in Settings > Media sending.",
                    "'다른 앱 위에 표시' 권한이 없어 StarLight가 화면에 떠 있을 때만 파일을 보낼 수 있어요. " +
                        "설정 > 미디어 전송에서 권한을 허용하세요.",
                )
            )
        }
    }

    /** Runs [block], logging its error under [what]; null if it failed. */
    private inline fun <T> catching(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Throwable) {
            val text = "$what: ${e.message ?: e}"
            if (project != null) project.logger.error("$TAG $text") else LOG.error { text }
            null
        }

    /** A name for a room that couldn't be resolved. */
    private fun labelOf(room: Any?): String =
        when (val room = ScriptValues.unwrap(room)) {
            null            -> "?"
            is CharSequence -> room.toString()
            else            -> directRoomOf(room)?.label
                ?: runCatching { MediaSharing.service?.resolver?.resolve(room)?.label }.getOrNull()
                ?: room.toString()
        }

    private fun roomInfo(room: RoomRegistry.Room): RoomInfo =
        RoomInfo(room.id, room.name, room.isGroupChat, room.lastSeen, room.userId)

    private fun service(): MediaSendService =
        MediaSharing.service ?: throw SendException("Sending files isn't ready yet")

    private companion object {
        const val TAG = "[MediaSharer]"
        const val MEMO_CHAT_LABEL = "나와의 채팅"
    }
}
