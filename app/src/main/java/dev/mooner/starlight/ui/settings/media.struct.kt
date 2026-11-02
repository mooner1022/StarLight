package dev.mooner.starlight.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import dev.mooner.configdsl.ConfigBuilder
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.options.InfoState
import dev.mooner.configdsl.options.OptionStatus
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.choice
import dev.mooner.configdsl.options.info
import dev.mooner.configdsl.options.seekbar
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.textList
import dev.mooner.configdsl.options.toggle
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.starlight.media.MediaSettings
import dev.mooner.starlight.media.MediaSharing
import dev.mooner.starlight.media.send.KakaoShare
import dev.mooner.starlight.media.send.LeaveTo
import dev.mooner.starlight.media.send.MediaSendService
import dev.mooner.starlight.media.send.SendHistory
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.utils.onSaveConfigAdapter
import dev.mooner.starlight.ui.config.options.page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val COLOR = "#FFB86C"
private const val COLOR_SUB = "#E09B55"
private const val TICKER_MILLIS = 3_000L

/** Settings > Media sending: how `MediaSharer.send` and `room.sendMedia` send files. */
internal fun ConfigBuilder.mediaPage(context: Context) = page {
    id = "media"
    title = "미디어 전송"
    icon = Icon.SEND
    iconTintColor = color { COLOR }
    shareData(GlobalConfig::getMutableData, GlobalConfig::onSaveConfigAdapter)
    structure {
        category {
            id = MediaSettings.CATEGORY_STATUS
            title = "상태"
            textColor = color { COLOR }
            items {
                info {
                    id = "kakao_status"
                    title = "카카오톡"
                    icon = Icon.SEND
                    bind(ticker { kakaoState(context) })
                }
                info {
                    id = "overlay_status"
                    title = "다른 앱 위에 표시"
                    description = "StarLight가 백그라운드에 있을 때 카카오톡을 열려면 필요해요. 눌러서 설정을 열어요."
                    icon = Icon.LAYERS
                    bind(ticker { overlayState(context) })
                    setOnClickListener { view ->
                        runCatching { KakaoShare.openOverlaySettings(view.context) }
                    }
                }
                // Only builds that declare the permission can be granted it.
                if (declaresOtherUsersPermission(context))
                    info {
                        id = "other_users_status"
                        title = "듀얼 메신저 카카오톡"
                        description = "다른 사용자(듀얼 메신저)의 카카오톡 방으로 선택창 없이 보내려면 권한을 부여해야 해요:\n" +
                            "adb shell pm grant ${context.packageName} ${KakaoShare.INTERACT_ACROSS_USERS}"
                        icon = Icon.ACCOUNT_BOX
                        bind(ticker { otherUsersState(context) })
                    }
            }
        }
        category {
            id = MediaSettings.CATEGORY_SEND
            title = "전송"
            textColor = color { COLOR_SUB }
            items {
                seekbar {
                    id = MediaSettings.CONF_TIMEOUT
                    title = "다운로드 제한 시간 (초)"
                    description = "연결과 데이터 대기마다 적용돼요. 스크립트의 timeout 옵션이 우선해요."
                    min = 5
                    max = 120
                    defaultValue = MediaSettings.DEF_TIMEOUT_SECONDS
                    icon = Icon.TIMER
                    iconTintColor = color { COLOR }
                }
                spinner {
                    id = MediaSettings.CONF_MAX_FILE
                    title = "최대 파일 크기"
                    items = MediaSettings.FILE_LIMIT_LABELS
                    defaultIndex = MediaSettings.DEF_FILE_LIMIT
                    icon = Icon.FILE
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_INTERVAL
                    title = "연속 전송 간격 (×0.1초)"
                    description = "연달아 보낸 파일이 누락되면 늘려 보세요."
                    min = 0
                    max = 50
                    defaultValue = 0
                    icon = Icon.ACCESS_TIME
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_RETENTION
                    title = "임시 파일 보관 시간 (초)"
                    description = "다운로드하거나 복사한 파일은 전송 후 이 시간이 지나면 삭제돼요. 큰 동영상은 늘려 주세요."
                    min = 10
                    max = 600
                    defaultValue = MediaSettings.DEF_RETENTION_SECONDS
                    icon = Icon.DELETE_SWEEP
                    iconTintColor = color { COLOR }
                }
                choice {
                    id = MediaSettings.CONF_AFTER_SEND
                    title = "전송 후"
                    description = "스크립트의 leave 옵션이 없을 때 적용돼요. 채팅방이 열려 있는 동안에는 알림이 오지 않으니 봇이라면 나가는 것이 좋아요."
                    option(LeaveTo.KEY_STAY, "카카오톡에 머물기")
                    option(LeaveTo.KEY_HOME, "홈 화면으로 이동")
                    option(LeaveTo.KEY_STARLIGHT, "StarLight로 돌아가기")
                    defaultKey = LeaveTo.KEY_STAY
                    icon = Icon.EXIT_TO_APP
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_RETURN_DELAY
                    title = "카카오톡 최대 체류 시간 (초)"
                    description = "카카오톡이 파일을 읽으면 바로 나가고, 읽는 것이 감지되지 않으면 이 시간 뒤에 나가요. " +
                        "지연 없이 호출한 MediaSharer.leave()에도 적용돼요."
                    min = 1
                    max = 30
                    defaultValue = MediaSettings.DEF_RETURN_DELAY_SECONDS
                    icon = Icon.TIMER
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_LEAVE_GRACE
                    title = "파일을 읽은 뒤 대기 (×0.1초)"
                    description = "카카오톡이 메시지를 올릴 시간이에요. 채팅방이 열려 있는 동안에는 알림이 오지 않으니 짧게 두고, " +
                        "전송이 누락되면 늘려 주세요."
                    min = 0
                    max = 30
                    defaultValue = MediaSettings.DEF_LEAVE_GRACE_TENTHS
                    icon = Icon.ACCESS_TIME
                    iconTintColor = color { COLOR }
                }
                toggle {
                    id = MediaSettings.CONF_LOG_SENDS
                    title = "전송 로그 남기기"
                    description = "모든 전송을 프로젝트 로그에 남겨요."
                    defaultValue = false
                    icon = Icon.LIST_BULLETED
                    iconTintColor = color { COLOR }
                }
            }
        }
        category {
            id = MediaSettings.CATEGORY_CACHE
            title = "캐시"
            textColor = color { COLOR_SUB }
            items {
                toggle {
                    id = MediaSettings.CONF_CACHE_DEFAULT
                    title = "기본으로 캐시 사용"
                    description = "다운로드·디코딩한 파일을 보관해 다시 보낼 때 내려받지 않아요. 스크립트의 cache 옵션이 우선해요."
                    defaultValue = false
                    icon = Icon.CLOUD_DOWNLOAD
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_CACHE_ITEMS
                    title = "최대 캐시 파일 수"
                    min = 10
                    max = 1000
                    defaultValue = MediaSettings.DEF_CACHE_ITEMS
                    icon = Icon.LAYERS
                    iconTintColor = color { COLOR }
                }
                spinner {
                    id = MediaSettings.CONF_CACHE_SIZE
                    title = "최대 캐시 크기"
                    items = MediaSettings.CACHE_LIMIT_LABELS
                    defaultIndex = MediaSettings.DEF_CACHE_LIMIT
                    icon = Icon.ARCHIVE
                    iconTintColor = color { COLOR }
                }
                button {
                    id = "clear_cache"
                    title = "캐시 비우기"
                    icon = Icon.DELETE
                    bindDescription(ticker { cacheUsage() })
                    action {
                        val service = MediaSharing.service ?: return@action
                        if (!confirm("캐시 비우기", "캐시된 파일을 모두 삭제할까요?"))
                            return@action
                        val count = withContext(Dispatchers.IO) { service.cache.clear() }
                        peek("파일 ${count}개를 삭제했어요", PeekType.SUCCESS)
                    }
                }
            }
        }
        category {
            id = MediaSettings.CATEGORY_ROOMS
            title = "채팅방"
            textColor = color { COLOR_SUB }
            items {
                textList {
                    id = "known_rooms"
                    title = "알려진 채팅방"
                    description = "메시지를 받은 채팅방이에요. 스크립트에서 채널 ID 대신 방 이름으로 지정할 수 있어요."
                    icon = Icon.LIST_BULLETED
                    copyable = true
                    emptyText = "아직 받은 메시지가 없어요"
                    lines = knownRooms()
                    bind(serviceFlow { service -> service.rooms.version.map { knownRooms() } })
                }
                button {
                    id = "forget_rooms"
                    title = "채팅방 목록 지우기"
                    icon = Icon.DELETE_SWEEP
                    action {
                        val service = MediaSharing.service ?: return@action
                        if (!confirm("채팅방 목록 지우기", "알려진 채팅방을 모두 지울까요?"))
                            return@action
                        withContext(Dispatchers.IO) { service.rooms.clear() }
                        peek("채팅방 목록을 지웠어요", PeekType.SUCCESS)
                    }
                }
            }
        }
        category {
            id = MediaSettings.CATEGORY_HISTORY
            title = "기록"
            textColor = color { COLOR_SUB }
            items {
                textList {
                    id = "recent_sends"
                    title = "최근 전송"
                    icon = Icon.SEND
                    monospace = true
                    copyable = true
                    emptyText = "아직 보낸 파일이 없어요"
                    lines = recentSends()
                    bind(serviceFlow { service -> service.history.version.map { recentSends() } })
                }
            }
        }
    }
}

private fun kakaoState(context: Context): InfoState =
    if (KakaoShare.isKakaoInstalled(context))
        InfoState("설치됨", OptionStatus.OK)
    else
        InfoState("설치되지 않음", OptionStatus.ERROR)

private fun overlayState(context: Context): InfoState =
    if (KakaoShare.canDrawOverlays(context))
        InfoState("허용됨", OptionStatus.OK)
    else
        InfoState("허용 필요", OptionStatus.WARNING)

/** Granted, or a warning if a known room belongs to another user's KakaoTalk. */
private fun otherUsersState(context: Context): InfoState {
    if (KakaoShare.canReachOtherUsers(context))
        return InfoState("허용됨", OptionStatus.OK)
    val myUserId = KakaoShare.myUserId()
    val hasOtherUserRooms = MediaSharing.service?.rooms?.list().orEmpty().any { it.userId != null && it.userId != myUserId }
    return if (hasOtherUserRooms)
        InfoState("권한 없음: 선택창 표시", OptionStatus.WARNING)
    else
        InfoState("권한 없음")
}

private fun declaresOtherUsersPermission(context: Context): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains(KakaoShare.INTERACT_ACROSS_USERS)
    }.getOrDefault(false)

private fun cacheUsage(): String {
    val usage = MediaSharing.service?.cache?.usage() ?: return "준비 중이에요"
    return "파일 ${usage.items}개, ${formatBytes(usage.bytes)}"
}

private fun knownRooms(): List<String> {
    val myUserId = KakaoShare.myUserId()
    return MediaSharing.service?.rooms?.list().orEmpty().map { room ->
        "${room.name} — ${room.id}" +
            (if (room.isGroupChat) "" else " (1:1)") +
            (room.userId?.takeIf { it != myUserId }?.let { " [사용자 $it]" } ?: "")
    }
}

private fun recentSends(): List<String> =
    MediaSharing.service?.history?.snapshot().orEmpty().map(::describe)

private fun describe(record: SendHistory.Record): String {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.time))
    val result = record.error?.let { "✗ $it" }
        ?: ("✓ ${record.files}" + if (record.failed > 0) " (!${record.failed})" else "")
    val via = record.via?.let { " [$it]" } ?: ""
    return "$time ${record.room}$via $result ${record.durationMillis}ms"
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1fGB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1fMB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.1fKB", bytes / (1L shl 10).toDouble())
        else               -> "${bytes}B"
    }

/** The flow [block] makes of the send service, looked up when the settings screen collects it. */
private fun <T> serviceFlow(block: (MediaSendService) -> Flow<T>): Flow<T> =
    flow {
        MediaSharing.service?.let { emitAll(block(it)) }
    }

/** Re-reads [block] every few seconds while the settings screen shows it. */
private fun <T> ticker(block: () -> T): Flow<T> =
    flow {
        while (true) {
            emit(block())
            delay(TICKER_MILLIS)
        }
    }.flowOn(Dispatchers.IO)
