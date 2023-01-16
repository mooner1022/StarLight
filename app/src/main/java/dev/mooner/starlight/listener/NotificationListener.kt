/*
 * NotificationListener.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

import android.app.Notification
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.widget.Toast
import androidx.core.app.NotificationCompat.*
import dev.mooner.starlight.listener.chat.ChatRoomImpl
import dev.mooner.starlight.listener.event.NotificationDismissEvent
import dev.mooner.starlight.listener.event.NotificationPostEvent
import dev.mooner.starlight.listener.legacy.ImageDB
import dev.mooner.starlight.listener.legacy.LegacyEvent
import dev.mooner.starlight.listener.legacy.Replier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.DeletedMessage
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.config.ConfigData
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.fireEvent
import dev.mooner.starlight.plugincore.utils.getInternalDirectory
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import kotlinx.serialization.decodeFromString
import java.util.*

private val LOG = LoggerFactory.logger {  }

class NotificationListener: NotificationListenerService() {

    private val isGlobalPowerOn: Boolean
        get() = GlobalConfig.category("general").getBoolean("global_power", true)

    private val replier = Replier { roomName, msg, hideToast ->
        val chatRoom = if (roomName == null) lastReceivedRoom else chatRooms[roomName]
        if (chatRoom == null) {
            if (!hideToast) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "메세지가 수신되지 않은 방 '$roomName' 에 메세지를 보낼 수 없습니다.", Toast.LENGTH_LONG).show()
                }
            }
            false
        } else {
            chatRoom.send(msg)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val pkgName = sbn.packageName
        val userId = sbn.userId

        val ruleKey = getRuleKey(pkgName, userId)
        if (ruleKey !in notificationRules) return

        if (sbn.notification.actions == null) return

        for (act in sbn.notification.actions) {
            if (act.remoteInputs != null && act.remoteInputs.isNotEmpty() && isGlobalPowerOn) {
                /*
                if ( || sbn.packageName != PACKAGE_KAKAO_TALK)
                    return
                */
                try {
                    val data = sbn.toMessage(this, act) ?: return

                    currentChatLogId = data.chatLogId

                    val room = data.room.name
                    val message = data.message
                    val sender = data.sender.name
                    val isGroupChat = data.room.isGroupChat

                    LOG.verbose {
                        """
                            message	: $message
                            sender	: $sender
                            room	: $room
                            session	: $act
                        """.trimIndent()
                    }

                    Session.projectManager.fireEvent<ProjectOnMessageEvent>(data) { project, e ->
                        e.printStackTrace()
                        LOG.error { "Failed to call event on '${project.info.name}': $e" }
                    }

                    if (GlobalConfig.category("legacy").getBoolean("use_legacy_event", false)) {
                        val imageDB = ImageDB(data.sender.profileBitmap)

                        Session.projectManager.fireEvent<LegacyEvent>(room, message, sender, isGroupChat, replier, imageDB) { project, e ->
                            e.printStackTrace()
                            LOG.error { "Failed to call event on '${project.info.name}': $e" }
                        }
                    }
                    //stopSelf()
                } catch (e: Exception) {
                    e.printStackTrace()
                    LOG.error(e)
                }
            }
        }

        NotificationPostEvent(sbn)
            .also(EventHandler::fireEventWithScope)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        LOG.verbose { "Notification removed with reason $reason" }

        if (sbn.notification.actions == null) return

        for (act in sbn.notification.actions) {
            if (act.remoteInputs != null && act.remoteInputs.isNotEmpty() && isGlobalPowerOn) {
                LOG.verbose { "currentChatLogId= $currentChatLogId" }

                sbn.toDeletedMessage()?.let { data ->
                    if (reason == REASON_APP_CANCEL && data.chatLogId == currentChatLogId) {
                        Session.projectManager.fireEvent<ProjectOnMessageDeleteEvent>(data) { project, e ->
                            e.printStackTrace()
                            LOG.verbose { "Failed to call event on '${project.info.name}': $e" }
                        }
                    }
                }
            }
        }

        NotificationDismissEvent(sbn, rankingMap, reason)
            .also(EventHandler::fireEventWithScope)
    }

    private fun StatusBarNotification.toMessage(context: Context, action: Notification.Action): Message? {
        val extras = notification.extras ?: return null

        val pkgName = packageName
        val userId = userId

        val ruleKey = getRuleKey(pkgName, userId)

        val message = extras[getRuleOrDefault(ruleKey, "message", EXTRA_TEXT)].toString()
        val sender = extras[getRuleOrDefault(ruleKey, "sender", EXTRA_TITLE)].toString()
        val room = extras[getRuleOrDefault(ruleKey, "room", EXTRA_SUMMARY_TEXT)]?.toString() ?: sender

        val profileBitmap = (notification.getLargeIcon().loadDrawable(applicationContext) as BitmapDrawable).bitmap
        val isGroupChat = extras.containsKey(getRuleOrDefault(ruleKey, "isGroupChat", EXTRA_SUMMARY_TEXT))
        val hasMention = extras[getRuleOrDefault(ruleKey, "hasMention", EXTRA_TEXT)] is SpannableString
        val chatLogId = extras.getLong(getRuleOrDefault(ruleKey, "chatLogId", "chatLogId"))

        val chatRoom: ChatRoom = chatRooms[room] ?: let {
            ChatRoomImpl(
                name = room,
                isGroupChat = isGroupChat,
                session = action,
                context = context
            ).also { nRoom ->
                chatRooms[room] = nRoom
            }
        }

        (chatRoom as ChatRoomImpl).setLastReceivedId(chatLogId)
        lastReceivedRoom = chatRoom

        return Message(
            message = message,
            sender = ChatSender(
                name = sender,
                profileBitmap = profileBitmap
            ),
            room = chatRoom,
            packageName = pkgName,
            hasMention = hasMention,
            chatLogId = chatLogId
        )
    }

    private fun StatusBarNotification.toDeletedMessage(): DeletedMessage? {
        val extras = notification.extras ?: return null

        val pkgName = packageName
        val userId = userId

        val ruleKey = getRuleKey(pkgName, userId)

        val message = extras[getRuleOrDefault(ruleKey, "message", EXTRA_TEXT)].toString()
        val sender = extras[getRuleOrDefault(ruleKey, "sender", EXTRA_TITLE)].toString()
        val room = extras[getRuleOrDefault(ruleKey, "room", EXTRA_SUMMARY_TEXT)]?.toString() ?: sender

        val chatLogId = extras.getLong(getRuleOrDefault(ruleKey, "chatLogId", "chatLogId"))

        val chatRoom: ChatRoom? = chatRooms[room]

        return DeletedMessage(
            message = message,
            sender = sender,
            room = chatRoom,
            packageName = pkgName,
            chatLogId = chatLogId
        )
    }

    init {
        updateRules()
    }

    companion object {

        private var currentChatLogId: Long = -1

        fun notifySent(id: Long) {
            currentChatLogId = -1
        }

        private var notificationRules: Map<String, Map<String, String>> = mapOf()

        private fun getRuleKey(pkg: String, userId: Int): String =
            "$pkg&$userId"

        private fun getRuleOrDefault(ruleKey: String, rule: String, default: String): String =
            notificationRules[ruleKey]?.get(rule) ?: default

        fun updateRules() {
            val file = getInternalDirectory().resolve(NotificationRulesActivity.FILE_NAME)
            if (!file.exists() || !file.isFile || !file.canRead()) return

            val data: ConfigData = Session.json.decodeFromString(file.readText())
            val rules: List<Map<String, TypedString>> = Session.json.decodeFromString(data["notification_rules"]!!["rules"]!!.castAs())

            notificationRules = rules.associate { rule ->
                val packageName: String = rule["package_name"]!!.castAs()
                val userId: Int = rule["user_id"]?.castAs<String>()?.toInt() ?: 0
                val params: List<Map<String, TypedString>> = Session.json.decodeFromString(rule["params"]!!.castAs())
                val mapped: MutableMap<String, String> = mutableMapOf()
                for (param in params) {
                    mapped[param["name"]!!.castAs()] = param["value"]!!.castAs()
                }

                getRuleKey(packageName, userId) to mapped
            }
            LOG.verbose { "notificationRules= $notificationRules" }
        }

        private val chatRooms: MutableMap<String, ChatRoom> = WeakHashMap()
        private var lastReceivedRoom: ChatRoom? = null

        fun hasRoom(roomName: String): Boolean =
            roomName in chatRooms

        fun send(message: String): Boolean =
            lastReceivedRoom?.send(message) ?: false
        fun sendTo(roomName: String, message: String): Boolean =
            chatRooms[roomName]?.send(message) ?: false

        fun markAsRead(): Boolean =
            lastReceivedRoom?.markAsRead() ?: false
        fun markAsRead(roomName: String): Boolean =
            chatRooms[roomName]?.markAsRead() ?: false
    }
}