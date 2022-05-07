package dev.mooner.starlight.listener

import dev.mooner.starlight.plugincore.chat.DeletedMessage
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class ProjectOnMessageDeleteEvent: ProjectEvent() {

    override val id: String = "on_message_deleted"

    override val name: String = "기본 메세지 삭제 이벤트"

    override val functionName: String = "onMessageDeleted"

    override val argTypes: Array<KClass<*>> = arrayOf(DeletedMessage::class)
}