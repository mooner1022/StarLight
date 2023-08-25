/*
 * ProjectOnMessageDeleteEvent.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.plugincore.chat.DeletedMessage
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class ProjectOnMessageDeleteEvent: ProjectEvent() {

    override val id: String = "delete"

    override val name: String = "기본 메세지 삭제 이벤트"

    override val functionName: String = "onMessageDeleted"

    override val argTypes: Array<KClass<*>> =
        arrayOf(DeletedMessage::class)
}