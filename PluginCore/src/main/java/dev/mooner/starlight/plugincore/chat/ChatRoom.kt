package dev.mooner.starlight.plugincore.chat

interface ChatRoom {

    val id: String

    val name: String

    val isGroupChat: Boolean

    val isDebugRoom: Boolean

    fun send(message: String): Boolean

    fun markAsRead(): Boolean
}