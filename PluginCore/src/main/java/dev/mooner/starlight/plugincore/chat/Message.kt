package dev.mooner.starlight.plugincore.chat

data class Message(
    val message: String,
    val sender: ChatSender,
    val room: ChatRoom,
    val packageName: String,
    val hasMention: Boolean
)