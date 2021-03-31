package com.mooner.starlight

import com.mooner.starlight.plugincore.annotations.StarLightEventListener
import com.mooner.starlight.plugincore.event.EventType
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.project.Project

@StarLightEventListener(EventType.INIT)
class DummyPlugin: StarlightPlugin() {
    override val usedProjects: List<Project>
        get() = TODO("Not yet implemented")
    override val name: String
        get() = "DummyPlugin"

    override fun onEnable() {
        super.onEnable()
        println("aaa")
    }
}