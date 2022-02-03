package dev.mooner.starlight.listener

import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class DefaultEvent: ProjectEvent() {

    override val id: String = "default"

    override val name: String = "기본 이벤트"

    override val functionName: String = "onMessage"

    override val argTypes: Array<KClass<*>> = arrayOf(Message::class)
}