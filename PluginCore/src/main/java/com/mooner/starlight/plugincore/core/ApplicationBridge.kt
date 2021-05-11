package com.mooner.starlight.plugincore.core

class ApplicationBridge {
    private var applicationListener: ((eventID: Int, args: Map<String, Any>) -> Unit)? = null

    fun connectApplication(onEvent: (eventID: Int, args: Map<String, Any>) -> Unit) {
        if (applicationListener != null) {
            throw IllegalAccessError("ApplicationListener already assigned")
        }
        applicationListener = onEvent
    }
}