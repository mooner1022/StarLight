/*
 * AndroidRParserSpec.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.specs

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.text.getSpans
import dev.mooner.starlight.listener.chat.ChatRoomImpl
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.chat.MessageParserSpec
import dev.mooner.starlight.plugincore.version.Version
import kotlin.reflect.full.declaredFunctions

class AndroidRParserSpec: MessageParserSpec {

    private val kakaoTalkLimitVersion = Version.fromString("9.7.0")
    private val chatRoomCache: MutableMap<Int, MutableMap<String, ChatRoom>> = hashMapOf()
    private val createBitmap = Icon::class.declaredFunctions.find { it.name == "getBitmap" }!!

    override val id: String = "android_r"

    override val name: String = "안드로이드 11 이상, 카카오톡 9.7.0 이상 메세지 분석 스펙"

    @RequiresApi(Build.VERSION_CODES.P)
    override fun parse(
        userId : Int,
        context: Context,
        sbn    : StatusBarNotification,
        actions: Array<Notification.Action>
    ): Message? {
        val notification = sbn.notification
        val extras = notification.extras
            ?: return null

        val message = extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()
        val senderName = extras.getString(NotificationCompat.EXTRA_TITLE).toString()

        //val senderId = extras.getParcelableArray(getRuleOrDefault(ruleKey, "senderId", EXTRA_MESSAGES))!![0]
        @Suppress("UNCHECKED_CAST")
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(NotificationCompat.EXTRA_MESSAGES, Bundle::class.java)!!
        } else {
            extras.getParcelableArray(NotificationCompat.EXTRA_MESSAGES)!!.let {
                val nArr = Array<Bundle?>(it.size) { null }
                it.forEachIndexed { index, parcelable -> nArr[index] = parcelable as Bundle }
                nArr
            }
        }

        val sender = messages[0].let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                it.getParcelable("sender_person", Person::class.java)
            else
                it.getParcelable("sender_person")
        }!!
        val senderId = sender.key

        val roomId = sbn.tag
        val room = extras.getString(NotificationCompat.EXTRA_SUB_TEXT)
            ?: extras.getString(NotificationCompat.EXTRA_SUMMARY_TEXT)
            ?: senderName

        //val image = extras.getBundle("android.wearable.EXTENSIONS")?.getParcelable("background")
        val profileBitmap = createBitmap.call(sender.icon!!) as Bitmap
        val isGroupChat = room != senderName
        val hasMention = extras.getCharSequence(NotificationCompat.EXTRA_TEXT) is SpannableString

        if (hasMention)
            println((extras.getCharSequence(NotificationCompat.EXTRA_TEXT) as SpannableString).getSpans<Any>().joinToString { it.toString() })

        val chatLogId = extras.getLong("chatLogId")

        val background: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getBundle("android.wearable.EXTENSIONS")
                ?.getParcelable("background", Bitmap::class.java)
        } else {
            extras.getBundle("android.wearable.EXTENSIONS")
                ?.getParcelable("background") as Bitmap?
        }

        val readAction = sbn.notification.actions[0]
        val sendAction = sbn.notification.actions[1]
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
                name = senderName,
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