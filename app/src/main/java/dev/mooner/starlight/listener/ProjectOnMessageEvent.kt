package dev.mooner.starlight.listener

import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class ProjectOnMessageEvent: ProjectEvent() {

    override val id: String = "on_message"

    override val name: String = "기본 메세지 수신 이벤트"

    override val functionName: String = "onMessage"

    override val argTypes: Array<KClass<*>> = arrayOf(Message::class)
}