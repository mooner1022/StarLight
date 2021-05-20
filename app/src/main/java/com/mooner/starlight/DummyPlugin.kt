package com.mooner.starlight

import com.mooner.starlight.plugincore.annotations.EventHandler
import com.mooner.starlight.plugincore.plugin.StarlightPlugin

class DummyPlugin: StarlightPlugin() {
    override val name: String
        get() = "DummyPlugin"

    @EventHandler
    override fun onEnable() {
        super.onEnable()
        println("aaa")
    }
}