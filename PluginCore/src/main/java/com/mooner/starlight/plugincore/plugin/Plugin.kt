package com.mooner.starlight.plugincore.plugin

interface Plugin {
    val name: String

    fun isEnabled(): Boolean
}