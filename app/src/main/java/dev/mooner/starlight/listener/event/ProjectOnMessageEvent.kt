/*
 * ProjectOnMessageEvent.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate

/**
 * A chat message arrived in a room of an app the notification rules cover; calls the script's `onMessage(event)`.
 *
 * The rules cover KakaoTalk unless they were changed. Called once per message, on the project's workers, in projects
 * that are compiled and on. Messages sent in the debug room call it too, in a project that is compiled, whether it is
 * on or not.
 *
 * @param event The message: its text, sender and room, and the room to reply to.
 * @see guide:events#event-functions
 * @see guide:app#notification-rules
 * @see guide:testing#the-debug-room
 */
class ProjectOnMessageEvent: ProjectEvent() {

    override val id: String = "create"

    override val name: String = "기본 메세지 수신 이벤트"

    override val description: String = translate {
        Locale.ENGLISH { "When a message arrives" }
        Locale.KOREAN  { "메시지를 받았을 때" }
    }

    override val functionName: String = "onMessage"

    override val functionComment: String = translate {
        Locale.ENGLISH {
            """
                |Called when a message with appropriate options was received.
                |The event object is described here:
                |https://starlight.mooner.dev/docs/app/reference/events#onmessage
            """.trimMargin()
        }
        Locale.KOREAN {
            """
                |메세지가 수신 되었을 때 이 함수가 호출됩니다.
                |이벤트 객체의 구조는 이 링크를 참조하세요:
                |https://starlight.mooner.dev/docs/app/reference/events#onmessage
            """.trimMargin()
        }
    }

    override val argTypes: Array<CodeGenerator.Argument<*>> =
        arrayOf("event" typedAs Message::class)
}