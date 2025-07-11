/*
 * settings.struct.kt created by Minki Moon(mooner1022) on 8/3/23, 10:11 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.toggle
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.ui.config.options.page
import dev.mooner.starlight.ui.config.options.singleCategoryPage
import dev.mooner.starlight.ui.settings.dev.startDevModeActivity
import dev.mooner.starlight.ui.settings.info.AppInfoActivity
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.ui.settings.solver.getProblemSolverStruct
import dev.mooner.starlight.ui.settings.update.CheckUpdateConfigActivity
import dev.mooner.starlight.utils.restartApplication
import dev.mooner.starlight.utils.showConfirmDialog
import dev.mooner.starlight.utils.startActivity
import dev.mooner.starlight.utils.startConfigActivity

internal fun SettingsFragment.getSettingsStruct() = config {
    singleCategoryPage {
        id = "general"
        title = "일반"
        icon = Icon.SETTINGS
        iconTintColor = color { "#5584AC" }
        items {
            toggle {
                id = "global_power"
                title = "전역 전원"
                description = "모든 프로젝트의 답장/처리 여부를 결정합니다."
                icon = Icon.POWER
                iconTintColor = color { "#7ACA8A" }
                defaultValue = true
            }
            toggle {
                id = "newbie_mode"
                title = "쉬운 사용 모드"
                description = "설정을 단순화하여 초심자도 사용이 쉽도록 변경합니다."
                icon = Icon.ECO
                defaultValue = false
                setOnValueChangedListener { _, _ ->
                    showConfirmDialog(
                        requireActivity(),
                        title = "설정을 적용하려면 앱을 재시작해야 합니다.",
                        message = "지금 앱을 재시작할까요?"
                    ) { confirm ->
                        if (confirm)
                            requireActivity().restartApplication()
                    }
                }
            }
            button {
                id = "restart_application"
                title = "앱 재시작"
                description = "모든 프로세스를 안전하게 종료하고 재시작합니다."
                icon = Icon.REFRESH
                iconTintColor = requireContext().getColor(R.color.code_orange)
                setOnClickListener(requireActivity()::restartApplication)
            }
        }
    }
    singleCategoryPage {
        id = "project"
        title = "프로젝트"
        icon = Icon.PROJECTS
        iconTintColor = color { "#B4CFB0" }
        items {
            toggle {
                id = "compile_animation"
                title = "컴파일 애니메이션"
                description = "컴파일 시 진행도 애니메이션을 부드럽게 조정합니다."
                icon = Icon.COMPRESS
                iconTintColor = color { "#FEAC5E" }
                defaultValue = true
            }
            toggle {
                id = "load_global_libraries"
                title = "전역 모듈 로드"
                description = "StarLight/modules 폴더를 모듈 로드 경로에 포함합니다."
                icon = Icon.FOLDER
                iconTintColor = color { "#4BC0C8" }
                defaultValue = false
                warnOnEnable {
                    """
                    |이 기능은 신뢰할 수 없는 코드를 기기에서 실행할 수 있으며, 이로 인해 발생하는 어떠한 상해나 손실도 본 앱의 개발자는 보장하지 않습니다.
                    |기능을 활성화 할까요?
                    """.trimMargin()
                }
            }
        }
    }
    singleCategoryPage {
        id = "plugin"
        title = "플러그인"
        icon = Icon.ARCHIVE
        iconTintColor = color { "#95D1CC" }
        items {
            toggle {
                id = "safe_mode"
                title = "안전 모드 (재시작 필요)"
                description = "플러그인 안전 모드를 활성화 합니다. 모든 플러그인을 로드 하지 않습니다."
                icon = Icon.LAYERS_CLEAR
                iconTintColor = color { "#95D1CC" }
                defaultValue = false
            }
            button {
                id = "restart_with_safe_mode"
                title = "안전 모드로 재시작"
                description = "안전 모드 활성화 후 앱을 재시작 합니다."
                icon = Icon.REFRESH
                iconTintColor = color { "#FF6F3C" }
                setOnClickListener(requireContext()::restartApplication)
                dependency = "safe_mode"
            }
        }
    }
    page {
        id = "notifications"
        title = "알림, 이벤트"
        icon = Icon.NOTIFICATIONS
        iconTintColor = color { "#98BAE7" }
        structure {
            category {
                id = "event"
                title = "이벤트"
                textColor = requireContext().getColor(R.color.main_bright)
                items {
                    button {
                        id = "read_noti_perm"
                        title = "알림 읽기 권한 설정"
                        icon = Icon.NOTIFICATIONS_ACTIVE
                        iconTintColor = color { "#C8E4B2" }
                        setOnClickListener { _ ->
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                    toggle {
                        id = "use_legacy_event"
                        title = "레거시 이벤트 사용"
                        description = "메신저봇이나 채자봇과 호환되는 이벤트를 사용합니다. (response)"
                        icon = Icon.BOOKMARK
                        iconTintColor = color { "#9ED2BE" }
                        defaultValue = false
                    }
                    toggle {
                        id = "log_received_message"
                        title = "수신 메세지 로그 표시"
                        description = "수신된 메세지를 로그에 표시합니다. ('내부 로그 표시' 활성화 필요)"
                        icon = Icon.MARK_CHAT_READ
                        iconTintColor = color { "#7EAA92" }
                        defaultValue = false
                    }
                }
            }
            category { 
                id = "noti"
                title = "알림, 패키지"
                textColor = requireContext().getColor(R.color.main_bright)
                items {
                    button {
                        id = "set_package_rules"
                        title = "패키지 규칙 설정"
                        description = "패키지 별 알림을 수신할 규칙을 설정합니다."
                        icon = Icon.DEVELOPER_BOARD
                        iconTintColor = color { "#7EAA92" }
                        setOnClickListener { _ ->
                            requireActivity().startActivity<NotificationRulesActivity>()
                        }
                    }
                    toggle {
                        id = "use_on_notification_posted"
                        title = "onNotificationPosted 이벤트 사용"
                        //description = "메신저봇의 onNotificationPosted 이벤트를 사용합니다."
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
                    }
                    button {
                        id = "magic"
                        title = "문제 해결 도우미"
                        description = "알림 수신 관련 문제를 해결할 수 있도록 돕습니다."
                        icon = Icon.ECO
                        iconTintColor = color { "#FFD9B7" }
                        setOnClickListener { _ ->
                            requireActivity().startConfigActivity(
                                title = "문제 해결 도우미",
                                subTitle = "알림 수신 관련 문제 해결을 돕습니다.",
                                struct = getProblemSolverStruct(),
                            )
                        }
                    }
                }
            }
        }
    }
    category {
        id = "info"
        title = "정보"
        textColor = requireContext().getColor(R.color.main_bright)
        items {
            button {
                id = "check_update"
                title = "업데이트 확인"
                icon = Icon.CLOUD_DOWNLOAD
                iconTintColor = color { "#A7D0CD" }
                setOnClickListener { _ ->
                    //checkUpdate()

                    requireActivity().startConfigActivity(CheckUpdateConfigActivity())
                }
            }
            button {
                id = "app_info"
                title = "앱 정보"
                icon = Icon.INFO
                iconTintColor = color { "#F1CA89" }
                setOnClickListener { _ ->
                    startActivity(Intent(context, AppInfoActivity::class.java))
                }
            }
            button {
                id = "help_dev"
                title = "개발자 돕기"
                description = "이 개발자는 자원봉사 중이에요.."
                icon = Icon.FAVORITE
                iconTintColor = color { "#FF90BC" }
                setOnClickListener { _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/mooner"))
                    startActivity(intent)
                }
            }
            if (GlobalConfig.category("dev").getBoolean("dev_mode") == true) {
                button {
                    id = "developer_mode"
                    title = "개발자 모드"
                    icon = Icon.DEVELOPER_MODE
                    iconTintColor = color { "#93B5C6" }
                    setOnClickListener(requireContext()::startDevModeActivity)
                }
            }
        }
    }
}

internal fun SettingsFragment.getNoobSettingStruct() = config {
    val mainColor = requireContext().getColor(R.color.main_bright)
    category {
        id = "general"
        title = "일반"
        textColor = mainColor
        items {
            toggle {
                id = "global_power"
                title = "전역 전원"
                description = "모든 프로젝트의 답장/처리 여부를 결정합니다."
                icon = Icon.POWER
                iconTintColor = color { "#7ACA8A" }
                defaultValue = false
            }
            toggle {
                id = "newbie_mode"
                title = "쉬운 사용 모드"
                description = "설정을 단순화하여 초심자도 쉽게 사용이 가능하도록 변경합니다."
                icon = Icon.ECO
                defaultValue = true
                setOnValueChangedListener { _, _ ->
                    showConfirmDialog(
                        requireActivity(),
                        title = "설정을 적용하려면 앱을 재시작해야 합니다.",
                        message = "지금 앱을 재시작할까요?"
                    ) { confirm ->
                        if (confirm)
                            requireActivity().restartApplication()
                    }
                }
            }
            button {
                id = "restart_application"
                title = "앱 재시작"
                description = "모든 프로세스를 안전하게 종료하고 재시작합니다."
                icon = Icon.REFRESH
                iconTintColor = requireContext().getColor(R.color.code_orange)
                setOnClickListener(requireActivity()::restartApplication)
            }
        }
    }
    category {
        id = "project"
        title = "프로젝트"
        textColor = mainColor
        items {
            toggle {
                id = "compile_animation"
                title = "컴파일 애니메이션"
                description = "컴파일 시 프로그레스 바의 애니메이션을 부드럽게 조정합니다."
                icon = Icon.COMPRESS
                iconTintColor = color { "#FEAC5E" }
                defaultValue = true
            }
        }
    }
    category {
        id = "notifications"
        title = "알림, 이벤트"
        textColor = mainColor
        items {
            button {
                id = "read_noti_perm"
                title = "알림 읽기 권한 설정"
                icon = Icon.NOTIFICATIONS_ACTIVE
                iconTintColor = color { "#C8E4B2" }
                setOnClickListener { _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            toggle {
                id = "use_legacy_event"
                title = "레거시 이벤트 사용"
                description = "메신저봇이나 채자봇과 호환되는 이벤트를 사용합니다. (response)"
                icon = Icon.BOOKMARK
                iconTintColor = color { "#9ED2BE" }
                defaultValue = false
            }
            toggle {
                id = "use_on_notification_posted"
                title = "onNotificationPosted 이벤트 사용"
                description = "메신저봇의 onNotificationPosted 이벤트를 사용합니다. 부하가 증가할 수 있습니다."
                icon = Icon.COMPRESS
                iconTintColor = color { "#87AAAA" }
                defaultValue = false
            }
        }
    }
    category {
        id = "info"
        title = "정보"
        textColor = mainColor
        items {
            button {
                id = "check_update"
                title = "업데이트 확인"
                icon = Icon.CLOUD_DOWNLOAD
                iconTintColor = color { "#A7D0CD" }
                setOnClickListener { _ ->
                    //checkUpdate()
                    requireContext().startConfigActivity(CheckUpdateConfigActivity())
                }
            }
            button {
                id = "app_info"
                title = "앱 정보"
                icon = Icon.INFO
                iconTintColor = color { "#F1CA89" }
                setOnClickListener { _ ->
                    startActivity(Intent(context, AppInfoActivity::class.java))
                }
            }
            button {
                id = "help_dev"
                title = "개발자 돕기"
                description = "이 개발자는 자원봉사 중이에요.."
                icon = Icon.FAVORITE
                iconTintColor = color { "#FF90BC" }
                setOnClickListener { _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/mooner"))
                    startActivity(intent)
                }
            }
            if (GlobalConfig.category("dev").getBoolean("dev_mode") == true) {
                button {
                    id = "developer_mode"
                    title = "개발자 모드"
                    icon = Icon.DEVELOPER_MODE
                    iconTintColor = color { "#93B5C6" }
                    setOnClickListener(requireContext()::startDevModeActivity)
                }
            }
        }
    }
}