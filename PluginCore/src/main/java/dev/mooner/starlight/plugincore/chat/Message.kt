package dev.mooner.starlight.plugincore.chat

import android.graphics.Bitmap

data class Message(
    val message     : String,
    val image       : Bitmap?,
    val sender      : ChatSender,
    val room        : ChatRoom,
    val packageName : String,
    val hasMention  : Boolean,
    val chatLogId   : Long,
)