package com.mooner.starlight.plugincore.chat

interface ChatRoom {
    val name: String

    val isGroupChat: Boolean

    val isDebugRoom: Boolean

    fun send(message: String): Boolean

    fun markAsRead(): Boolean
}