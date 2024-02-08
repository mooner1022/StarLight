/*
 * DefaultParserSpec.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.specs

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import androidx.core.app.NotificationCompat
import dev.mooner.starlight.listener.chat.ChatRoomImpl
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.chat.MessageParserSpec

class DefaultParserSpec: MessageParserSpec {

    private val chatRoomCache: MutableMap<Int, MutableMap<String, ChatRoom>> = hashMapOf()

    override val id: String = "default"

    override val name: String = "기본 메세지 분석 스펙"

    override fun parse(
        userId: Int,
        context: Context,
        sbn: StatusBarNotification,
        actions: Array<Notification.Action>
    ): Message? {
        val notification = sbn.notification
        val extras = notification.extras
            ?: return null

        val message = extras.getString(NotificationCompat.EXTRA_TEXT).toString()
        val sender = extras.getString(NotificationCompat.EXTRA_TITLE).toString()

        //val senderId = extras.getParcelableArray(getRuleOrDefault(ruleKey, "senderId", EXTRA_MESSAGES))!![0]
        val senderId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (extras.getParcelableArray(NotificationCompat.EXTRA_MESSAGES)?.get(0) as Bundle?)
                ?.getParcelable<android.app.Person>("sender_person")
                ?.key
        } else {
            null
        }
        val roomId = sbn.tag
        val room = extras.getString(NotificationCompat.EXTRA_SUMMARY_TEXT, sender)

        val profileBitmap = (notification.getLargeIcon().loadDrawable(context) as BitmapDrawable).bitmap
        val isGroupChat = extras.containsKey(NotificationCompat.EXTRA_SUMMARY_TEXT)
        val hasMention = extras.getCharSequence(NotificationCompat.EXTRA_MESSAGES) is SpannableString
        val chatLogId = extras.getLong("chatLogId")

        val background: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getBundle("android.wearable.EXTENSIONS")
                ?.getParcelable("background", Bitmap::class.java)
        } else {
            extras.getBundle("android.wearable.EXTENSIONS")
                ?.getParcelable("background") as Bitmap?
        }

        val readAction = actions[0]
        val sendAction = actions[1]
        val chatRoom: ChatRoom = chatRoomCache[userId]?.get(room) ?: let {
            ChatRoomImpl(
                id = roomId,
                name = room,
                isGroupChat = isGroupChat,
                sendSession = sendAction,
                readSession = readAction,
                context = context
            ).also { nRoom ->
                if (userId !in chatRoomCache)
                    chatRoomCache[userId] = hashMapOf()
                chatRoomCache[userId]!![room] = nRoom
            }
        }

        return Message(
            message = message,
            image = background,
            sender = ChatSender(
                name = sender,
                id = senderId,
                profileBitmap = profileBitmap
            ),
            room = chatRoom,
            packageName = sbn.packageName,
            hasMention = hasMention,
            chatLogId = chatLogId
        )
    }
}