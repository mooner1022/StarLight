/*
 * NotificationListener.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import androidx.core.app.NotificationCompat.EXTRA_TEXT
import androidx.core.app.NotificationCompat.EXTRA_TITLE
import dev.mooner.starlight.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.listener.chat.ChatRoomImpl
import dev.mooner.starlight.listener.event.LegacyEvent
import dev.mooner.starlight.listener.event.OnNotificationPostedEvent
import dev.mooner.starlight.listener.event.ProjectOnMessageDeleteEvent
import dev.mooner.starlight.listener.event.ProjectOnMessageEvent
import dev.mooner.starlight.listener.legacy.ImageDB
import dev.mooner.starlight.listener.legacy.Replier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.DeletedMessage
import dev.mooner.starlight.plugincore.chat.ParserSpecManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.fireEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.debugTranslated
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.ui.settings.notifications.RuleData
import dev.mooner.starlight.utils.decodeIfNotBlank
import dev.mooner.starlight.utils.isNoobMode
import kotlinx.coroutines.*
import java.util.*

private val LOG = LoggerFactory.logger {  }

private typealias RoomID   = String
private typealias RoomName = String

@Suppress("DEPRECATION")
class NotificationListener: NotificationListenerService() {

    private var isGlobalPowerOn : Boolean = true
    private var isNewbieMode    : Boolean = false
    private var legacyEvent     : Boolean = false
    private var useNPostedEvent : Boolean = false
    private var logRecMessage   : Boolean = false

    private val eventReceiverScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val replier = Replier { roomName, msg, hideToast ->
        val chatRoom = roomName?.let(::getRoomByName) ?: lastReceivedRoom
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

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateConfig()
        eventReceiverScope.launch {
            EventHandler.on(this, ::onGlobalConfigUpdated)
        }
        LOG.debug { "Listener connected" }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        eventReceiverScope.coroutineContext.cancelChildren()
        LOG.debug { "Listener disconnected from system" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isGlobalPowerOn || sbn.notification.actions == null || !ApplicationSession.isInitComplete) return

        val ts = System.currentTimeMillis()
        val pkgName = sbn.packageName
        val userId = sbn.userId

        if (useNPostedEvent) {
            Session.projectManager.fireEvent<OnNotificationPostedEvent>(sbn) { project, e ->
                LOG.error {
                    translate {
                        Locale.ENGLISH { "Failed to call event on '${project.info.name}': $e" }
                        Locale.KOREAN  { "프로젝트 '${project.info.name}'의 이벤트 호출 실패: $e" }
                    }
                }
            }
        }

        if (rules.isEmpty()) return
        var idx = 0
        val appliedRule = rules.find { ++idx; it.packageName == pkgName && it.userId == userId }
            ?: return
        val isDefaultRule = idx == 1

        if (sbn.notification.actions == null)
            return

        for (act in sbn.notification.actions) {
            if (act.remoteInputs != null && act.remoteInputs.isNotEmpty()) {
                /*
                if ( || sbn.packageName != PACKAGE_KAKAO_TALK)
                    return
                */
                try {
                    val data = ParserSpecManager.getSpecById(appliedRule.parserSpecId)
                        ?.parse(userId, applicationContext, sbn, sbn.notification.actions)
                        ?: return
                    //val data = sbn.toMessage(this, ruleKey, act) ?: return
                    (data.room as ChatRoomImpl).setLastReceivedId(data.chatLogId)
                    lastReceivedRoom = data.room

                    if (data.chatLogId == currentChatLogId) {
                        LOG.verbose { "Detected multiple event listeners" }
                        return
                    }
                    currentChatLogId = data.chatLogId

                    val roomName    = data.room.name
                    val roomID      = data.room.id
                    val message     = data.message
                    val sender      = data.sender.name
                    val senderId    = data.sender.id
                    val isGroupChat = data.room.isGroupChat

                    if (isDefaultRule && roomID !in chatRooms) {
                        roomIdMap[roomName] = roomID
                        chatRooms[roomID] = data.room
                    }

                    if (logRecMessage) {
                        LOG.verbose {
                            """
                            pkgName(userID) : $pkgName($userId)
                            userHash: ${data.sender.profileHash}
                            senderId: $senderId
                            message	: $message
                            sender	: $sender
                            room	: $roomName
                            """.trimIndent()
                        }
                    }

                    Session.projectManager.fireEvent<ProjectOnMessageEvent>(data) { project, e ->
                        e.printStackTrace()
                        LOG.error {
                            translate {
                                Locale.ENGLISH { "Failed to call event on '${project.info.name}': $e" }
                                Locale.KOREAN  { "프로젝트 '${project.info.name}'의 이벤트 호출 실패: $e" }
                            }
                        }
                    }

                    if (legacyEvent) {
                        val imageDB = ImageDB(data.sender.profileBitmap)

                        Session.projectManager.fireEvent<LegacyEvent>(roomName, message, sender, isGroupChat, replier, imageDB) { project, e ->
                            e.printStackTrace()
                            LOG.error {
                                translate {
                                    Locale.ENGLISH { "Failed to call event on '${project.info.name}': $e" }
                                    Locale.KOREAN  { "프로젝트 '${project.info.name}'의 이벤트 호출 실패: $e" }
                                }
                            }
                        }
                    }
                    break
                    //stopSelf()
                } catch (e: Exception) {
                    e.printStackTrace()
                    LOG.error {
                        translate {
                            Locale.ENGLISH { """
                                |Failed to parse message content:
                                |message : ${e.localizedMessage}
                                |cause   : ${e.cause}
                                |${e.stackTraceToString()}
                            """.trimMargin() }
                            Locale.KOREAN  { """
                                |메세지 해석에 실패했습니다:
                                |message : ${e.localizedMessage}
                                |cause   : ${e.cause}
                                |${e.stackTraceToString()}
                            """.trimMargin() }
                        }
                    }
                }
            }
        }
        LOG.verbose { "es = ${System.currentTimeMillis() - ts}" }

        Events.Notification.Post(sbn, applicationContext)
            .also(EventHandler::fireEventWithScope)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn.packageName != PACKAGE_KAKAO_TALK)
            return
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

        Events.Notification.Dismiss(sbn, rankingMap, reason)
            .also(EventHandler::fireEventWithScope)
    }

    private fun StatusBarNotification.toDeletedMessage(): DeletedMessage? {
        val extras = notification.extras ?: return null

        val pkgName = packageName
        //val ruleKey = getRuleKey(pkgName, userId)

        val message = extras.getString(EXTRA_TEXT).toString()
        val sender = extras.getString(EXTRA_TITLE).toString()
        val roomId = this.tag

        val chatLogId = extras.getLong("chatLogId")

        val chatRoom: ChatRoom? = chatRooms[roomId]

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
        private val roomIdMap: MutableMap<RoomName, RoomID> = hashMapOf()
        private val chatRooms: MutableMap<RoomID, ChatRoom> = WeakHashMap()
        private var lastReceivedRoom: ChatRoom? = null
        private var rules: List<RuleData> = arrayListOf()

        fun notifySent() {
            currentChatLogId = -1
        }

        fun updateRules() {
            if (isNoobMode) {
                val rule = detectRule()
                rules = listOf(rule)
                return
            }
            val file = getStarLightDirectory().resolve(NotificationRulesActivity.FILE_NAME)
            if (!file.exists() || !file.isFile || !file.canRead()) return

            try {
                rules = file.readText()
                    .let(Session.json::decodeIfNotBlank)
                    ?: arrayListOf()
            } catch (e: Exception) {
                e.printStackTrace()
                LOG.error { "패키지 규칙을 해석하는 데 실패했어요." }
            }

            LOG.verbose { "notificationRules= $rules" }
        }

        private fun detectRule(): RuleData {
            var specId = "default"

            val kakaoTalkVersion = ApplicationSession.kakaoTalkVersion
            val androidVersion = Build.VERSION.SDK_INT

            if (kakaoTalkVersion != null &&
                kakaoTalkVersion.newerThan(Version.fromString("9.7.0")) &&
                androidVersion >= Build.VERSION_CODES.R) {
                specId = "android_r"
            }

            return RuleData(
                packageName = PACKAGE_KAKAO_TALK,
                userId = 0,
                parserSpecId = specId
            )
        }

        private fun getRoomByName(roomName: RoomName): ChatRoom? =
            roomIdMap[roomName]?.let(chatRooms::get)

        fun getRoomNames(): Set<String> =
            roomIdMap.keys

        fun hasRoom(roomName: String): Boolean =
            roomName in roomIdMap

        fun send(message: String): Boolean =
            lastReceivedRoom?.send(message) ?: false
        fun sendTo(roomName: String, message: String): Boolean =
            getRoomByName(roomName)?.send(message) ?: false

        fun markAsRead(): Boolean =
            lastReceivedRoom?.markAsRead() ?: false
        fun markAsRead(roomName: String): Boolean =
            getRoomByName(roomName)?.markAsRead() ?: false
    }

    private fun onGlobalConfigUpdated(event: Events.Config.GlobalConfigUpdate) =
        updateConfig()

    private fun updateConfig() {
        GlobalConfig
            .category("general")
            .getBoolean("global_power", isGlobalPowerOn)
            .let { globalPowerState ->
                if (globalPowerState != isGlobalPowerOn) {
                    isGlobalPowerOn = globalPowerState
                    LOG.debugTranslated {
                        Locale.ENGLISH { "Global power state updated to " + if (globalPowerState) "ON" else "OFF" }
                        Locale.KOREAN  { "전역 전원 설정이 ${if (globalPowerState) "켜짐" else "꺼짐"}으로 변경되었어요." }
                    }
                }
            }

        legacyEvent = GlobalConfig
            .category("notifications")
            .getBoolean("use_legacy_event", legacyEvent)
        useNPostedEvent = GlobalConfig
            .category("beta_features")
            .getBoolean("use_on_notification_posted", useNPostedEvent)
        logRecMessage = GlobalConfig
            .category("notifications")
            .getBoolean("log_received_message", logRecMessage)
    }
}