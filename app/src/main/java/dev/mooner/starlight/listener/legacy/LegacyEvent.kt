package dev.mooner.starlight.listener.legacy

import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class LegacyEvent: ProjectEvent() {

    override val id: String = "default_legacy"

    override val name: String = "레거시 호환 모드"

    override val functionName: String = "response"

    override val argTypes: Array<KClass<*>> =
        arrayOf(String::class, String::class, String::class, Boolean::class, Replier::class, ImageDB::class)
}