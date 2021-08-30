package com.mooner.starlight.plugincore.interfaces

interface ChatRoom {
    val name: String

    fun send(message: String): Boolean

    fun markAsRead(): Boolean
}