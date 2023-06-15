/*
 * ProjectOnMessageEvent.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class ProjectOnMessageEvent: ProjectEvent() {

    override val id: String = "on_message"

    override val name: String = "기본 메세지 수신 이벤트"

    override val functionName: String = "onMessage"

    override val argTypes: Array<KClass<*>> =
        arrayOf(Message::class)
}