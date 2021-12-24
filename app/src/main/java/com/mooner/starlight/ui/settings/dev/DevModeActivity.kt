package com.mooner.starlight.ui.settings.dev

import android.content.Context
import com.mooner.starlight.plugincore.Info
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.globalConfig
import com.mooner.starlight.plugincore.Session.pluginManager
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
                id = "beta_features"
                title = "실험적 기능"
                textColor = color { "#706EB9" }
                items = items {
                    button {
                        id = "caution"
                        title = "주의"
                        description = "아래 설정들은 실험적이며, 사용 시 앱이 작동하지 않거나 기기에 손상을 줄 수 있습니다.\n이 기능들을 사용함으로서 발생하는 문제에 대해 개발자는 책임을 지지 않습니다."
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF865E" }
                        onClickListener = {}
                    }
                    toggle {
                        id = "load_external_dex_libs"
                        title = "외부 dex 라이브러리 로드"
                        description = "/libs 디렉터리 내부의 .dex 라이브러리를 로드합니다."
                        icon = Icon.CREATE_NEW_FOLDER
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
                    }
                    toggle {
                        id = "change_thread_pool_size"
                        title = "Thread Pool 크기 변경"
                        description = "프로젝트가 실행되는 Thread pool의 크기를 가변적으로 변경하는 설정을 추가합니다."
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
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
                    debugInfo("widgets", globalConfig.getCategory("widgets").getString("ids", WIDGET_DEF_STRING))
                    debugInfo("plugins", pluginManager.getPlugins().joinToString { it.info.id })
                    //debugInfo("libs", )
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