/*
 * MarkdownSender.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.chat

import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.languages.neonjs.whileWaitingForAnotherThread
import dev.mooner.starlight.media.MediaSharing
import dev.mooner.starlight.media.room.Target
import dev.mooner.starlight.media.send.KakaoShare
import dev.mooner.starlight.media.send.KakaoTextAttachment
import dev.mooner.starlight.media.send.SendLog
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val LOG = LoggerFactory.logger {  }

/**
 * Sends a message as Markdown, for MessengerBot's Markdown methods: through KakaoTalk's share screen, as
 * `MediaSharer.sendText` does with `{ markdown: true }`.
 *
 * KakaoTalk opens the room and sends the text, so the room needs no reply session. It is sent one KakaoTalk start at a
 * time with files, and KakaoTalk is left afterwards as **미디어 전송** says. Only KakaoTalk reads the Markdown mark.
 */
object MarkdownSender {

    /** How long a call waits for KakaoTalk to be started, as sends before it may still be going. */
    private const val START_TIMEOUT_SECONDS = 15L

    private val log = object : SendLog {
        override val projectName: String = "-"
        override fun info(message: String) = LOG.info { message }
        override fun warn(message: String) = LOG.warn { message }
    }

    /**
     * Sends [text] as Markdown to the room with ID [roomId] of the app [packageName], through the KakaoTalk of the
     * Android user [userId] (null when not known).
     *
     * The caller waits until KakaoTalk was started, up to fifteen seconds; a NeonJS script's context is released
     * meanwhile. A message still waiting after that stays queued and is sent later.
     *
     * @return Whether KakaoTalk was started with the message, or it is still queued: `false` for an app other than
     *   KakaoTalk, a room without a numeric ID, when starting failed, or when the caller was interrupted. It does not
     *   say KakaoTalk delivered the message.
     */
    fun send(packageName: String, roomId: BigInteger, text: String, userId: Int? = null): Boolean {
        if (packageName != KakaoShare.KAKAO_PACKAGE || roomId.signum() == 0)
            return false
        val attachment = KakaoTextAttachment(markdown = true).toJson()
        val service = MediaSharing.service
        return try {
            if (service == null) {
                // Sending files isn't set up: start KakaoTalk directly, without the queue.
                KakaoShare.shareText(GlobalApplication.requireContext(), roomId.toLong(), text, attachment, userId)
                return true
            }
            val sending = service.sendText(Target(roomId.toLong(), "room $roomId", userId), text, attachment, null, log)
            whileWaitingForAnotherThread { sending.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            true
        } catch (_: TimeoutException) {
            // Sends before it are still going; the message keeps its place in the queue.
            LOG.debug { "Markdown to room $roomId is still queued after ${START_TIMEOUT_SECONDS}s" }
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (e: Exception) {
            LOG.debug { "Sending Markdown to room $roomId failed: $e" }
            false
        }
    }
}
