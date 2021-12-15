package com.mooner.starlight.listener.legacy

import com.mooner.starlight.plugincore.event.Event

class LegacyEvent: Event() {

    override val id: String = "default_legacy"

    override val name: String = "레거시 호환 모드"

    override val eventName: String = "response"
}