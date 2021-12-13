package com.mooner.starlight.listener

import com.mooner.starlight.plugincore.event.Event

class DefaultEvent: Event() {

    override val id: String = "default"
    override val name: String = "기본 이벤트"
    override val eventName: String = "onMessage"
    override val compatibleLanguageId: List<String> = listOf("JS_RHINO")
}