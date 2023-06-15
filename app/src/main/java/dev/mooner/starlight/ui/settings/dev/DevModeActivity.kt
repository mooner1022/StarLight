/*
 * DevModeActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.dev

import android.content.Context
import android.content.res.Configuration
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.getInternalDirectory
import dev.mooner.starlight.utils.*
import java.util.*

fun Context.startDevModeActivity() {
    val activityId = UUID.randomUUID().toString()
    startConfigActivity(
        uuid = activityId,
        title = "설정",
        subTitle = "개발자 모드",
        saved = GlobalConfig.getAllConfigs(),
        struct = config {
            category {
                id = "dev_mode_config"
                title = "개발자 모드"
                textColor = getColor(R.color.main_bright)
                items {
                    toggle {
                        id = "show_internal_log"
                        title = "내부 로그 표시"
                        description = "앱 내부의 디버깅용 로그를 표시합니다."
                        icon = Icon.MARK_CHAT_UNREAD
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
                    }
                    toggle {
                        dependency = "show_internal_log"
                        id = "write_internal_log"
                        title = "내부 로그 기록"
                        description = "앱 내부의 디버깅용 로그를 로그 파일에 기록합니다."
                        icon = Icon.EDIT
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
                    }
                    button {
                        id = "make_error"
                        title = "에러 발생"
                        description = "고의적으로 에러를 발생시킵니다."
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF5C58" }
                        setOnClickListener { _ ->
                            throw Exception("Expected error created from dev mode")
                        }
                    }
                    button {
                        id = "disable_dev_mode"
                        title = "개발자 모드 비활성화"
                        icon = Icon.DEVELOPER_BOARD_OFF
                        iconTintColor = color { "#FF8243" }
                        setOnClickListener { _ ->
                            GlobalConfig.edit {
                                category("dev")["dev_mode"] = false
                            }
                            finishConfigActivity(activityId)
                        }
                    }
                }
            }
            category {
                id = "beta_features"
                title = "실험적 기능"
                textColor = getColor(R.color.main_bright)
                items {
                    button {
                        id = "caution"
                        title = "주의"
                        description = "아래 설정들은 실험적이며, 사용 시 앱이 작동하지 않거나 기기에 손상을 줄 수 있습니다.\n이 기능들을 사용함으로서 발생하는 문제에 대해 개발자는 책임을 지지 않습니다."
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF865E" }
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
                textColor = getColor(R.color.main_bright)
                items {
                    fun debugInfo(title: String, value: String) = button {
                        id = title
                        this.title = title
                        description = value
                    }
                    val (screenWidth, screenHeight) = getScreenSizeDp(this@startDevModeActivity)
                    val layoutModeString = when(layoutMode) {
                        LAYOUT_DEFAULT -> "LAYOUT_DEFAULT"
                        LAYOUT_TABLET  -> "LAYOUT_TABLET"
                        LAYOUT_SLIM    -> "LAYOUT_SLIM"
                        else           -> "UNKNOWN"
                    }
                    val uiMode = nightModeFlags
                    val uiModeString = when(uiMode) {
                        Configuration.UI_MODE_NIGHT_YES -> "UI_MODE_NIGHT_YES"
                        Configuration.UI_MODE_NIGHT_NO -> "UI_MODE_NIGHT_NO"
                        Configuration.UI_MODE_NIGHT_UNDEFINED -> "UI_MODE_NIGHT_UNDEFINED"
                        else -> "UNKNOWN"
                    }
                    debugInfo("globalPower", GlobalConfig.category("general").getBoolean("global_power").toString())
                    debugInfo("screenSizeDp", "$screenWidth x $screenHeight")
                    debugInfo("layoutMode", "$layoutMode ($layoutModeString)")
                    debugInfo("uiMode", "$uiMode ($uiModeString)")
                    debugInfo("widgets", GlobalConfig.category("widgets").getString("ids", WIDGET_DEF_STRING))
                    debugInfo("plugins", pluginManager.plugins.joinToString { it.info.id })
                    //debugInfo("libs", )
                    debugInfo("pluginSafeMode", GlobalConfig.category("plugin").getBoolean("safe_mode").toString())
                    debugInfo("baseDirectory", getStarLightDirectory().path)
                    debugInfo("PLUGINCORE_VERSION", dev.mooner.starlight.plugincore.Info.PLUGINCORE_VERSION.toString())
                }
            }
        },
        onConfigChanged = GlobalConfig::onSaveConfigAdapter
    )
}