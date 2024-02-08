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

class ProjectOnMessageEvent: ProjectEvent() {

    override val id: String = "create"

    override val name: String = "기본 메세지 수신 이벤트"

    override val functionName: String = "onMessage"

    override val functionComment: String = translate {
        Locale.ENGLISH {
            """
                |Called when a message with appropriate options was received.
                |Visit URL below for further options on event structure:
                |https://gist.github.com/mooner1022/4473bc39241a9b4af6ebf44fb17a2f26
            """.trimMargin()
        }
        Locale.KOREAN {
            """
                |메세지가 수신 되었을 때 이 함수가 호출됩니다.
                |이벤트 객체의 구조는 이 링크를 참조하세요:
                |https://gist.github.com/mooner1022/4473bc39241a9b4af6ebf44fb17a2f26
            """.trimMargin()
        }
    }

    override val argTypes: Array<CodeGenerator.Argument<*>> =
        arrayOf("event" typedAs Message::class)
}