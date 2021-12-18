package com.mooner.starlight.ui.settings.dev

import android.content.Context
import com.mooner.starlight.plugincore.Info
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.globalConfig
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.utils.*
import java.util.*

fun Context.startDevModeActivity() {
    val activityId = UUID.randomUUID().toString()

    startConfigActivity(
        id = activityId,
        title = "설정",
        subTitle = "개발자 모드",
        saved = Session.globalConfig.getAllConfigs(),
        items = config {
            category {
                id = "dev_mode_config"
                title = "개발자 모드"
                textColor = color { "#706EB9" }
                items = items {
                    toggle {
                        id = "show_internal_log"
                        title = "내부 로그 표시"
                        description = "앱 내부의 디버깅용 로그를 표시합니다."
                        icon = Icon.MARK_CHAT_UNREAD
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
                    }
                    button {
                        id = "make_error"
                        title = "에러 발생"
                        description = "고의적으로 에러를 발생시킵니다."
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF5C58" }
                        onClickListener = {
                            throw Exception("Expected error created from dev mode")
                        }
                    }
                    button {
                        id = "disable_dev_mode"
                        title = "개발자 모드 비활성화"
                        icon = Icon.DEVELOPER_BOARD_OFF
                        iconTintColor = color { "#FF8243" }
                        onClickListener = {
                            Session.globalConfig.edit {
                                getCategory("dev")["dev_mode"] = false
                            }
                            finishConfigActivity(activityId)
                        }
                    }
                }
            }
            category {
                id = "debug_info"
                title = "debug info"
                textColor = color { "#706EB9" }
                items = items {
                    fun debugInfo(title: String, value: String) = button {
                        id = title
                        this.title = title
                        description = value
                        onClickListener = {}
                    }
                    val layoutModeString = when(layoutMode) {
                        LAYOUT_DEFAULT -> "LAYOUT_DEFAULT"
                        LAYOUT_TABLET -> "LAYOUT_TABLET"
                        else -> "UNKNOWN"
                    }
                    debugInfo("globalPower", globalConfig.getCategory("general").getBoolean("global_power").toString())
                    debugInfo("layoutMode", "$layoutMode ($layoutModeString)")
                    debugInfo("pluginSafeMode", globalConfig.getCategory("plugin").getBoolean("safe_mode").toString())
                    debugInfo("baseDirectory", FileUtils.getInternalDirectory().path)
                    debugInfo("PLUGINCORE_VERSION", Info.PLUGINCORE_VERSION.toString())
                }
            }
        },
        onConfigChanged = { parentId, id, view, data ->
            globalConfig.onSaveConfigAdapter(parentId, id, view, data)
        }
    )
}