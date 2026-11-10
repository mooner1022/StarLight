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
import dev.mooner.starlight.R
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
    title = context.getString(R.string.media_settings_page_title)
    icon = Icon.SEND
    iconTintColor = color { COLOR }
    shareData(GlobalConfig::getMutableData, GlobalConfig::onSaveConfigAdapter)
    structure {
        category {
            id = MediaSettings.CATEGORY_STATUS
            title = context.getString(R.string.media_settings_category_status)
            textColor = color { COLOR }
            items {
                info {
                    id = "kakao_status"
                    title = context.getString(R.string.media_settings_kakao_status_title)
                    icon = Icon.SEND
                    bind(ticker { kakaoState(context) })
                }
                info {
                    id = "overlay_status"
                    title = context.getString(R.string.media_settings_overlay_status_title)
                    description = context.getString(R.string.media_settings_overlay_status_desc)
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
                        title = context.getString(R.string.media_settings_other_users_status_title)
                        description = context.getString(
                            R.string.media_settings_other_users_status_desc,
                            context.packageName,
                            KakaoShare.INTERACT_ACROSS_USERS,
                        )
                        icon = Icon.ACCOUNT_BOX
                        bind(ticker { otherUsersState(context) })
                    }
            }
        }
        category {
            id = MediaSettings.CATEGORY_SEND
            title = context.getString(R.string.media_settings_category_send)
            textColor = color { COLOR_SUB }
            items {
                seekbar {
                    id = MediaSettings.CONF_TIMEOUT
                    title = context.getString(R.string.media_settings_download_timeout_title)
                    description = context.getString(R.string.media_settings_download_timeout_desc)
                    min = 5
                    max = 120
                    defaultValue = MediaSettings.DEF_TIMEOUT_SECONDS
                    icon = Icon.TIMER
                    iconTintColor = color { COLOR }
                }
                spinner {
                    id = MediaSettings.CONF_MAX_FILE
                    title = context.getString(R.string.media_settings_max_file_size_title)
                    items = MediaSettings.FILE_LIMIT_LABELS
                    defaultIndex = MediaSettings.DEF_FILE_LIMIT
                    icon = Icon.FILE
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_INTERVAL
                    title = context.getString(R.string.media_settings_send_interval_title)
                    description = context.getString(R.string.media_settings_send_interval_desc)
                    min = 0
                    max = 50
                    defaultValue = 0
                    icon = Icon.ACCESS_TIME
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_RETENTION
                    title = context.getString(R.string.media_settings_temp_retention_title)
                    description = context.getString(R.string.media_settings_temp_retention_desc)
                    min = 10
                    max = 600
                    defaultValue = MediaSettings.DEF_RETENTION_SECONDS
                    icon = Icon.DELETE_SWEEP
                    iconTintColor = color { COLOR }
                }
                choice {
                    id = MediaSettings.CONF_AFTER_SEND
                    title = context.getString(R.string.media_settings_after_send_title)
                    description = context.getString(R.string.media_settings_after_send_desc)
                    option(LeaveTo.KEY_STAY, context.getString(R.string.media_settings_after_send_stay))
                    option(LeaveTo.KEY_HOME, context.getString(R.string.media_settings_after_send_home))
                    option(LeaveTo.KEY_STARLIGHT, context.getString(R.string.media_settings_after_send_starlight))
                    defaultKey = LeaveTo.KEY_STAY
                    icon = Icon.EXIT_TO_APP
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_RETURN_DELAY
                    title = context.getString(R.string.media_settings_return_delay_title)
                    description = context.getString(R.string.media_settings_return_delay_desc)
                    min = 1
                    max = 30
                    defaultValue = MediaSettings.DEF_RETURN_DELAY_SECONDS
                    icon = Icon.TIMER
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_LEAVE_GRACE
                    title = context.getString(R.string.media_settings_leave_grace_title)
                    description = context.getString(R.string.media_settings_leave_grace_desc)
                    min = 0
                    max = 30
                    defaultValue = MediaSettings.DEF_LEAVE_GRACE_TENTHS
                    icon = Icon.ACCESS_TIME
                    iconTintColor = color { COLOR }
                }
                toggle {
                    id = MediaSettings.CONF_LOG_SENDS
                    title = context.getString(R.string.media_settings_log_sends_title)
                    description = context.getString(R.string.media_settings_log_sends_desc)
                    defaultValue = false
                    icon = Icon.LIST_BULLETED
                    iconTintColor = color { COLOR }
                }
            }
        }
        category {
            id = MediaSettings.CATEGORY_CACHE
            title = context.getString(R.string.media_settings_category_cache)
            textColor = color { COLOR_SUB }
            items {
                toggle {
                    id = MediaSettings.CONF_CACHE_DEFAULT
                    title = context.getString(R.string.media_settings_cache_by_default_title)
                    description = context.getString(R.string.media_settings_cache_by_default_desc)
                    defaultValue = false
                    icon = Icon.CLOUD_DOWNLOAD
                    iconTintColor = color { COLOR }
                }
                seekbar {
                    id = MediaSettings.CONF_CACHE_ITEMS
                    title = context.getString(R.string.media_settings_cache_max_items_title)
                    min = 10
                    max = 1000
                    defaultValue = MediaSettings.DEF_CACHE_ITEMS
                    icon = Icon.LAYERS
                    iconTintColor = color { COLOR }
                }
                spinner {
                    id = MediaSettings.CONF_CACHE_SIZE
                    title = context.getString(R.string.media_settings_cache_max_size_title)
                    items = MediaSettings.CACHE_LIMIT_LABELS
                    defaultIndex = MediaSettings.DEF_CACHE_LIMIT
                    icon = Icon.ARCHIVE
                    iconTintColor = color { COLOR }
                }
                button {
                    id = "clear_cache"
                    title = context.getString(R.string.media_settings_clear_cache_title)
                    icon = Icon.DELETE
                    bindDescription(ticker { cacheUsage(context) })
                    action {
                        val service = MediaSharing.service ?: return@action
                        if (!confirm(
                                context.getString(R.string.media_settings_clear_cache_title),
                                context.getString(R.string.media_settings_clear_cache_confirm),
                            ))
                            return@action
                        val count = withContext(Dispatchers.IO) { service.cache.clear() }
                        peek(context.getString(R.string.media_settings_clear_cache_done, count), PeekType.SUCCESS)
                    }
                }
            }
        }
        category {
            id = MediaSettings.CATEGORY_ROOMS
            title = context.getString(R.string.media_settings_category_rooms)
            textColor = color { COLOR_SUB }
            items {
                textList {
                    id = "known_rooms"
                    title = context.getString(R.string.media_settings_known_rooms_title)
                    description = context.getString(R.string.media_settings_known_rooms_desc)
                    icon = Icon.LIST_BULLETED
                    copyable = true
                    emptyText = context.getString(R.string.media_settings_known_rooms_empty)
                    lines = knownRooms(context)
                    bind(serviceFlow { service -> service.rooms.version.map { knownRooms(context) } })
                }
                button {
                    id = "forget_rooms"
                    title = context.getString(R.string.media_settings_forget_rooms_title)
                    icon = Icon.DELETE_SWEEP
                    action {
                        val service = MediaSharing.service ?: return@action
                        if (!confirm(
                                context.getString(R.string.media_settings_forget_rooms_title),
                                context.getString(R.string.media_settings_forget_rooms_confirm),
                            ))
                            return@action
                        withContext(Dispatchers.IO) { service.rooms.clear() }
                        peek(context.getString(R.string.media_settings_forget_rooms_done), PeekType.SUCCESS)
                    }
                }
            }
        }
        category {
            id = MediaSettings.CATEGORY_HISTORY
            title = context.getString(R.string.media_settings_category_history)
            textColor = color { COLOR_SUB }
            items {
                textList {
                    id = "recent_sends"
                    title = context.getString(R.string.media_settings_recent_sends_title)
                    icon = Icon.SEND
                    monospace = true
                    copyable = true
                    emptyText = context.getString(R.string.media_settings_recent_sends_empty)
                    lines = recentSends()
                    bind(serviceFlow { service -> service.history.version.map { recentSends() } })
                }
            }
        }
    }
}

private fun kakaoState(context: Context): InfoState =
    if (KakaoShare.isKakaoInstalled(context))
        InfoState(context.getString(R.string.media_settings_kakao_status_installed), OptionStatus.OK)
    else
        InfoState(context.getString(R.string.media_settings_kakao_status_not_installed), OptionStatus.ERROR)

private fun overlayState(context: Context): InfoState =
    if (KakaoShare.canDrawOverlays(context))
        InfoState(context.getString(R.string.media_settings_status_allowed), OptionStatus.OK)
    else
        InfoState(context.getString(R.string.media_settings_overlay_status_needed), OptionStatus.WARNING)

/** Granted, or a warning if a known room belongs to another user's KakaoTalk. */
private fun otherUsersState(context: Context): InfoState {
    if (KakaoShare.canReachOtherUsers(context))
        return InfoState(context.getString(R.string.media_settings_status_allowed), OptionStatus.OK)
    val myUserId = KakaoShare.myUserId()
    val hasOtherUserRooms = MediaSharing.service?.rooms?.list().orEmpty().any { it.userId != null && it.userId != myUserId }
    return if (hasOtherUserRooms)
        InfoState(context.getString(R.string.media_settings_other_users_status_chooser), OptionStatus.WARNING)
    else
        InfoState(context.getString(R.string.media_settings_other_users_status_not_granted))
}

private fun declaresOtherUsersPermission(context: Context): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains(KakaoShare.INTERACT_ACROSS_USERS)
    }.getOrDefault(false)

private fun cacheUsage(context: Context): String {
    val usage = MediaSharing.service?.cache?.usage()
        ?: return context.getString(R.string.media_settings_cache_usage_preparing)
    return context.getString(R.string.media_settings_cache_usage, usage.items, formatBytes(usage.bytes))
}

private fun knownRooms(context: Context): List<String> {
    val myUserId = KakaoShare.myUserId()
    return MediaSharing.service?.rooms?.list().orEmpty().map { room ->
        "${room.name} — ${room.id}" +
            (if (room.isGroupChat) "" else " (1:1)") +
            (room.userId?.takeIf { it != myUserId }?.let { " " + context.getString(R.string.media_settings_known_rooms_user, it) } ?: "")
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
