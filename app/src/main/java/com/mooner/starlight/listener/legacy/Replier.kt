package com.mooner.starlight.listener.legacy

data class Replier(
    val onSend: (room: String?, message: String, hideToast: Boolean) -> Boolean
) {
    fun reply(room: String, message: String, hideToast: Boolean = false): Boolean = onSend(room, message, hideToast)

    fun reply(message: String) = onSend(null, message, false)
}