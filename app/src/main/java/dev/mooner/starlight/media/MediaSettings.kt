/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media

import dev.mooner.starlight.media.send.LeaveTo
import dev.mooner.starlight.media.send.SendSettings
import dev.mooner.starlight.plugincore.config.GlobalConfig

/** What Settings > Media sending holds, as the send service reads it. */
internal object MediaSettings {

    const val CATEGORY_STATUS = "media_status"
    const val CATEGORY_SEND = "media_send"
    const val CATEGORY_CACHE = "media_cache"
    const val CATEGORY_ROOMS = "media_rooms"
    const val CATEGORY_HISTORY = "media_history"

    const val CONF_TIMEOUT = "download_timeout"
    const val CONF_MAX_FILE = "max_file_size"
    const val CONF_INTERVAL = "send_interval"
    const val CONF_RETENTION = "temp_retention"
    const val CONF_AFTER_SEND = "after_send"
    const val CONF_RETURN_DELAY = "return_delay"
    const val CONF_LEAVE_GRACE = "leave_grace"
    const val CONF_LOG_SENDS = "log_sends"
    const val CONF_CACHE_DEFAULT = "cache_by_default"
    const val CONF_CACHE_ITEMS = "cache_max_items"
    const val CONF_CACHE_SIZE = "cache_max_size"

    const val DEF_TIMEOUT_SECONDS = 30
    const val DEF_RETENTION_SECONDS = 60
    const val DEF_RETURN_DELAY_SECONDS = 5
    const val DEF_LEAVE_GRACE_TENTHS = 5
    const val DEF_CACHE_ITEMS = 200

    val FILE_LIMITS = longArrayOf(10L shl 20, 50L shl 20, 100L shl 20, 300L shl 20, 1L shl 30)
    val FILE_LIMIT_LABELS = listOf("10MB", "50MB", "100MB", "300MB", "1GB")
    const val DEF_FILE_LIMIT = 3

    val CACHE_LIMITS = longArrayOf(50L shl 20, 100L shl 20, 250L shl 20, 500L shl 20, 1L shl 30, 2L shl 30)
    val CACHE_LIMIT_LABELS = listOf("50MB", "100MB", "250MB", "500MB", "1GB", "2GB")
    const val DEF_CACHE_LIMIT = 3

    /** Read on every use, so changes apply to the next send without a restart. */
    fun read(): SendSettings {
        val send = GlobalConfig.category(CATEGORY_SEND)
        val cache = GlobalConfig.category(CATEGORY_CACHE)
        return SendSettings(
            downloadTimeoutMillis = send.getInt(CONF_TIMEOUT, DEF_TIMEOUT_SECONDS) * 1000,
            maxFileBytes = FILE_LIMITS.getOrElse(send.getInt(CONF_MAX_FILE, DEF_FILE_LIMIT)) { FILE_LIMITS[DEF_FILE_LIMIT] },
            tempRetentionMillis = send.getInt(CONF_RETENTION, DEF_RETENTION_SECONDS) * 1000L,
            sendIntervalMillis = send.getInt(CONF_INTERVAL, 0) * 100L,
            leaveTo = LeaveTo.ofSetting(send.getString(CONF_AFTER_SEND)),
            leaveMaxWaitMillis = send.getInt(CONF_RETURN_DELAY, DEF_RETURN_DELAY_SECONDS) * 1000L,
            leaveGraceMillis = send.getInt(CONF_LEAVE_GRACE, DEF_LEAVE_GRACE_TENTHS) * 100L,
            logSends = send.getBoolean(CONF_LOG_SENDS, false),
            cacheByDefault = cache.getBoolean(CONF_CACHE_DEFAULT, false),
            cacheMaxItems = cache.getInt(CONF_CACHE_ITEMS, DEF_CACHE_ITEMS),
            cacheMaxBytes = CACHE_LIMITS.getOrElse(cache.getInt(CONF_CACHE_SIZE, DEF_CACHE_LIMIT)) { CACHE_LIMITS[DEF_CACHE_LIMIT] },
        )
    }
}
