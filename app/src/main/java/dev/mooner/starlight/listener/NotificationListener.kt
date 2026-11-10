/*
 * NotificationListener.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

import android.app.Notification
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
import dev.mooner.starlight.listener.event.Api2CommandEvent
import dev.mooner.starlight.listener.event.Api2MessageEvent
import dev.mooner.starlight.listener.event.Api2NotificationPostedEvent
import dev.mooner.starlight.listener.event.Api2NotificationRemovedEvent
import dev.mooner.starlight.listener.event.LegacyEvent
import dev.mooner.starlight.listener.event.OnNotificationPostedEvent
import dev.mooner.starlight.listener.event.ProjectOnMessageDeleteEvent
import dev.mooner.starlight.listener.event.ProjectOnMessageEvent
import dev.mooner.starlight.listener.legacy.ImageDB
import dev.mooner.starlight.listener.legacy.Replier
import dev.mooner.starlight.listener.specs.ParserSpecPolicy
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.DeletedMessage
import dev.mooner.starlight.plugincore.chat.ParserSpecManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.fireEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.debugTranslated
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.ui.settings.notifications.RuleData
import dev.mooner.starlight.utils.decodeIfNotBlank
import dev.mooner.starlight.utils.getKakaoTalkVersion
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

private val LOG = LoggerFactory.logger {  }

private typealias RoomID   = String
private typealias RoomName = String

@Suppress("DEPRECATION")
class NotificationListener: NotificationListenerService() {

    private var isGlobalPowerOn : Boolean = true
    private var useNPostedEvent : Boolean = false
    private var logRecMessage   : Boolean = false

    private val eventReceiverScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (!isGlobalPowerOn || !ApplicationSession.isInitComplete || sbn.packageName == packageName) return

        if (useNPostedEvent) {
            Session.projectManager.fireEvent<OnNotificationPostedEvent>(sbn, onFailure = ::logEventFailure)
            Session.projectManager.fireEvent<Api2NotificationPostedEvent>(sbn, onFailure = ::logEventFailure)
        }

        sbn.notification.actions?.let { actions ->
            handleMessage(sbn, actions, rankingMap)
        }

        Events.Notification.Post(sbn, applicationContext)
            .also(EventHandler::fireEventWithScope)
    }

    private fun handleMessage(sbn: StatusBarNotification, actions: Array<Notification.Action>, rankingMap: RankingMap?) {
        val ts = System.currentTimeMillis()
        val pkgName = sbn.packageName
        val userId = sbn.userId

        if (isAutoRule && pkgName == PACKAGE_KAKAO_TALK)
            refreshAutoRule()
        if (rules.isEmpty()) return
        var idx = 0
        val appliedRule = rules.find { ++idx; it.packageName == pkgName && it.userId == userId }
            ?: return
        val isDefaultRule = idx == 1

        for (act in actions) {
            if (act.remoteInputs != null && act.remoteInputs.isNotEmpty()) {
                try {
                    val ranking = rankingMap?.let { map ->
                        Ranking().takeIf { map.getRanking(sbn.key, it) }
                    }
                    val data = ParserSpecManager.getSpecById(appliedRule.parserSpecId)
                        ?.parse(userId, applicationContext, sbn, actions, ranking)
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

                    if (isDefaultRule) {
                        roomIdMap[roomName] = roomID
                        chatRooms[roomID] = data.room
                    }

                    if (logRecMessage) {
                        LOG.info {
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

                    Session.projectManager.fireEvent<ProjectOnMessageEvent>(data, onFailure = ::logEventFailure)
                    Session.projectManager.fireEvent<Api2MessageEvent>(data, onFailure = ::logEventFailure)
                    Session.projectManager.fireEvent<Api2CommandEvent>(data, onFailure = ::logEventFailure)

                    // Each project's levels decide whether it gets this one.
                    run {
                        val imageDB = ImageDB(data.sender.profileBitmap)
                        val replier = getOrCreateReplier(roomID)

                        Session.projectManager.fireEvent<LegacyEvent>(roomName, message, sender, isGroupChat, replier, imageDB, pkgName) { project, e ->
                            e.printStackTrace()
                            LOG.error {
                                translate {
                                    Locale.ENGLISH { "Failed to call event on '${project.info.name}': $e" }
                                    Locale.KOREAN  { "프로젝트 '${project.info.name}'의 이벤트 호출 실패: $e" }
                                }
                            }
                        }
                    }

                    EventHandler.fireEventWithScope(Events.Message.Create(data))
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
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (useNPostedEvent && isGlobalPowerOn && ApplicationSession.isInitComplete && sbn.packageName != packageName)
            Session.projectManager.fireEvent<Api2NotificationRemovedEvent>(sbn, rankingMap, reason, onFailure = ::logEventFailure)
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

    /**
     * Detects the rule again when KakaoTalk is not the version the rule was detected for.
     * KakaoTalk updates while the listener keeps running, and may not have been looked up yet when it started.
     */
    private fun refreshAutoRule() {
        // Below Android 11 every version reads the same.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // The package manager caches this, and drops it when the package changes.
        val version = try {
            getKakaoTalkVersion()
        } catch (_: Exception) {
            null
        }
        if (version == detectedKakaoTalkVersion) return

        detectedKakaoTalkVersion = version
        rules = listOf(detectRule(version))
        LOG.verbose { "notificationRules(auto)= $rules" }
    }

    private fun getOrCreateReplier(roomId: RoomID): Replier {
        return replierCache[roomId] ?: Replier { room, message, hideToast ->
            val chatRoom = room?.let(::getRoomByName) ?: chatRooms[roomId]
            if (chatRoom == null) {
                if (!hideToast) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "메세지가 수신되지 않은 방 '$room' 에 메세지를 보낼 수 없습니다.", Toast.LENGTH_LONG).show()
                    }
                }
                false
            } else {
                chatRoom.send(message)
            }
        }.also {
            replierCache[roomId] = it
        }
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
        private val roomIdMap: MutableMap<RoomName, RoomID> = ConcurrentHashMap()
        private val chatRooms: MutableMap<RoomID, ChatRoom> = ConcurrentHashMap()
        private val replierCache: MutableMap<RoomID, Replier> = ConcurrentHashMap()
        private var lastReceivedRoom: ChatRoom? = null
        private var rules: List<RuleData> = arrayListOf()
        private var isAutoRule: Boolean = false
        /** The KakaoTalk version name the automatic rule was detected for. */
        private var detectedKakaoTalkVersion: String? = null

        fun notifySent() {
            currentChatLogId = -1
        }

        fun updateRules() {
            isAutoRule = GlobalConfig.category("noti_rules").getBoolean("auto_rule", true)
            if (isAutoRule) {
                detectedKakaoTalkVersion = ApplicationSession.kakaoTalkVersion?.toString()
                rules = listOf(detectRule(detectedKakaoTalkVersion))
                LOG.verbose { "notificationRules(auto)= $rules" }
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

        /** The rule for a KakaoTalk of [versionName], null when it could not be told. */
        private fun detectRule(versionName: String?): RuleData {
            val version = versionName
                ?.takeIf(Version::check)
                ?.let(Version::fromString)

            return RuleData(
                packageName = PACKAGE_KAKAO_TALK,
                userId = 0,
                parserSpecId = ParserSpecPolicy.specIdFor(Build.VERSION.SDK_INT, version)
            )
        }

        private fun getRoomByName(roomName: RoomName): ChatRoom? =
            roomIdMap[roomName]?.let(chatRooms::get)

        /** The rooms messages came from so far. */
        fun getRooms(): Collection<ChatRoom> =
            chatRooms.values

        /** The room named [nameOrId], or with that ID. */
        fun getRoom(nameOrId: String): ChatRoom? =
            getRoomByName(nameOrId) ?: chatRooms[nameOrId]

        fun getRoomNames(): Set<String> =
            roomIdMap.keys

        fun hasRoom(roomName: String): Boolean =
            roomName in roomIdMap

        fun send(message: String): Boolean =
            lastReceivedRoom?.send(message) ?: false
        fun sendTo(roomName: String, message: String): Boolean =
            getRoomByName(roomName)?.send(message) ?: false
        fun sendToID(roomId: RoomID, message: String): Boolean =
            chatRooms[roomId]?.send(message) ?: false

        fun markAsRead(): Boolean =
            lastReceivedRoom?.markAsRead() ?: false
        fun markAsRead(roomName: String): Boolean =
            getRoomByName(roomName)?.markAsRead() ?: false
        fun markAsReadByID(roomID: RoomID): Boolean =
            chatRooms[roomID]?.markAsRead() ?: false
    }

    private fun logEventFailure(project: Project, e: Throwable) {
        e.printStackTrace()
        LOG.error {
            translate {
                Locale.ENGLISH { "Failed to call event on '${project.info.name}': $e" }
                Locale.KOREAN  { "프로젝트 '${project.info.name}'의 이벤트 호출 실패: $e" }
            }
        }
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

        useNPostedEvent = GlobalConfig
            .category("notifications")
            .getSubCategory("noti")
            ?.getBoolean("use_on_notification_posted", useNPostedEvent)
            ?: useNPostedEvent
        logRecMessage = GlobalConfig
            .category("notifications")
            .getSubCategory("event")
            ?.getBoolean("log_received_message", logRecMessage)
            ?: logRecMessage
    }
}