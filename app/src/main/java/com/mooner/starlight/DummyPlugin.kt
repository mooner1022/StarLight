package com.mooner.starlight

import com.mooner.starlight.plugincore.annotations.StarLightEventListener
import com.mooner.starlight.plugincore.event.EventType
import com.mooner.starlight.plugincore.plugin.StarlightPlugin

@StarLightEventListener(EventType.INIT)
class DummyPlugin: StarlightPlugin() {
    override val name: String
        get() = "DummyPlugin"

    override fun onEnable() {
        super.onEnable()
        println("aaa")
    }
}