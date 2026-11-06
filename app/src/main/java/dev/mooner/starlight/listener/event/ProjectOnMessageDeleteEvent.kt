/*
 * ProjectOnMessageDeleteEvent.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.plugincore.chat.DeletedMessage
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate

/**
 * The latest KakaoTalk message StarLight received was deleted; calls the script's `onMessageDeleted(event)`.
 *
 * StarLight notices a deletion when KakaoTalk itself removes that message's notification, so only the latest message
 * counts, only in KakaoTalk, and a removal for another reason looks the same. After a bot sends to any KakaoTalk room,
 * nothing counts until the next message arrives. Called on the project's workers, in projects that are compiled and on.
 *
 * @param event The deleted message: its text, sender's name, room and chat log ID. Its `room` is `null` when no
 *   message from that room has arrived since StarLight started.
 */
class ProjectOnMessageDeleteEvent: ProjectEvent() {

    override val id: String = "delete"

    override val name: String = "기본 메세지 삭제 이벤트"

    override val description: String = translate {
        Locale.ENGLISH { "When the latest KakaoTalk message is deleted" }
        Locale.KOREAN  { "카카오톡에서 가장 최근 메시지가 삭제됐을 때" }
    }

    override val functionName: String = "onMessageDeleted"

    override val argTypes: Array<CodeGenerator.Argument<*>> =
        arrayOf("event" typedAs DeletedMessage::class)
}