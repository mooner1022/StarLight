package com.mooner.starlight.plugincore.models

import com.mooner.starlight.plugincore.interfaces.ChatRoom

data class Message(
    val message: String,
    val sender: ChatSender,
    val room: ChatRoom,
    val packageName: String
)