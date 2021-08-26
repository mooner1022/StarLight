package com.mooner.starlight.plugincore.models

import com.mooner.starlight.plugincore.interfaces.ChatRoom

data class DebugChatRoom(
    override val name: String,
    val onSend: (message: String) -> Unit
): ChatRoom {
    override fun send(message: String): Boolean {
        onSend(message)
        return true
    }
}
