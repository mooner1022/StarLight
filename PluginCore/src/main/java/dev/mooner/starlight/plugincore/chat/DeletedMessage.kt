package dev.mooner.starlight.plugincore.chat

data class DeletedMessage(
    val message: String,
    val sender: String,
    val room: ChatRoom?,
    val packageName: String,
    val chatLogId: Long
)