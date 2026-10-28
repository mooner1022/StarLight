/*
 * settings.struct.kt created by Minki Moon(mooner1022) on 8/3/23, 10:11 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.mooner.configdsl.ConfigOption
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.RootConfigOption
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import dev.mooner.starlight.CF_LOG_BUFFER_SIZE
import dev.mooner.starlight.CF_LOG_RETENTION
import dev.mooner.starlight.R
import dev.mooner.starlight.logging.LogCollector
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

internal const val OPTION_NOTIFICATION_ACCESS  = "notification_access"
internal const val OPTION_BATTERY_OPTIMIZATION = "battery_optimization"
internal const val OPTION_CLEAR_LOGS           = "clear_logs"

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
        id = "permissions"
        title = "권한"
        icon = Icon.LOCK
        iconTintColor = color { "#C8E4B2" }
        items {
            button {
                id = OPTION_NOTIFICATION_ACCESS
                title = "알림 접근 권한"
                description = requireContext().notificationAccessDescription()
                icon = Icon.NOTIFICATIONS_ACTIVE
                iconTintColor = color { "#C8E4B2" }
                setOnClickListener { view ->
                    refreshStatusOnReturn(view)
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            button {
                id = OPTION_BATTERY_OPTIMIZATION
                title = "배터리 최적화 예외"
                description = requireContext().batteryOptimizationDescription()
                icon = Icon.ECO
                iconTintColor = color { "#9ED2BE" }
                setOnClickListener { view ->
                    refreshStatusOnReturn(view)
                    view.context.openBatteryOptimizationSettings()
                }
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
                description = "컴파일이 끝난 뒤 진행 표시줄이 끝까지 차오르는 것을 잠시 보여줍니다. 끄면 바로 사라집니다."
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
                title = "안전 모드"
                description = "플러그인을 불러오지 않고 앱을 실행합니다. 앱을 재시작하면 적용됩니다."
                icon = Icon.LAYERS_CLEAR
                iconTintColor = color { "#95D1CC" }
                defaultValue = false
                setOnValueChangedListener { view, _ ->
                    view.context.confirmRestart()
                }
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
                        description = "수신된 메세지의 방, 보낸 사람, 내용을 로그에 기록합니다."
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
                        description = "모든 앱의 알림을 메신저봇의 onNotificationPosted 이벤트로 전달합니다. 부하가 증가할 수 있습니다."
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
    singleCategoryPage {
        id = "logs"
        title = "로그"
        icon = Icon.LIST_BULLETED
        iconTintColor = color { "#F59193" }
        items {
            spinner {
                id = CF_LOG_RETENTION
                title = "로그 파일 보관 기간"
                description = "기간이 지난 로그 파일은 앱을 시작할 때 삭제됩니다."
                icon = Icon.ARCHIVE
                iconTintColor = color { "#F59193" }
                items = LogCollector.retentionDays.map { if (it == 0) "제한 없음" else "${it}일" }
                defaultIndex = LogCollector.DEFAULT_RETENTION_INDEX
            }
            spinner {
                id = CF_LOG_BUFFER_SIZE
                title = "로그 탭 표시 개수"
                description = "로그 탭에 보관할 최근 로그의 개수입니다."
                icon = Icon.LIST_BULLETED
                iconTintColor = color { "#F9AE91" }
                items = LogCollector.bufferSizes.map { "${it}개" }
                defaultIndex = 0
            }
            button {
                id = OPTION_CLEAR_LOGS
                title = "로그 파일 정리"
                description = requireContext().logFilesDescription()
                icon = Icon.DELETE_SWEEP
                iconTintColor = color { "#FF5C58" }
                setOnClickListener { view ->
                    showConfirmDialog(
                        view.context,
                        title = "로그 파일을 정리할까요?",
                        message = "현재 실행 중인 로그를 제외한 모든 로그 파일이 삭제됩니다."
                    ) { confirm ->
                        if (!confirm)
                            return@showConfirmDialog
                        LogCollector.clearLogFiles()
                        refreshStatus()
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

internal fun List<ConfigOption<*, *>>.findOptionById(id: String): ConfigOption<*, *>? {
    for (option in this) {
        if (option.id == id)
            return option
        if (option is RootConfigOption<*, *>)
            option.childOptions.findOptionById(id)?.let { return it }
    }
    return null
}

internal fun Context.notificationAccessDescription(): String =
    if (packageName in NotificationManagerCompat.getEnabledListenerPackages(this))
        "허용됨"
    else
        "허용되지 않음 · 알림을 받으려면 허용해야 합니다."

internal fun Context.batteryOptimizationDescription(): String =
    if (getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) == true)
        "예외로 설정됨"
    else
        "설정되지 않음 · 백그라운드에서 앱이 종료될 수 있습니다."

internal fun Context.logFilesDescription(): String {
    val (count, size) = LogCollector.logFileStats()
    return "로그 파일 ${count}개 · ${Formatter.formatShortFileSize(this, size)}"
}

private fun SettingsFragment.refreshStatusOnReturn(view: View) {
    val owner = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<LifecycleOwner>()
        .firstOrNull()
        ?: return
    owner.lifecycle.addObserver(object : LifecycleEventObserver {
        private var paused = false

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_PAUSE ->
                    paused = true
                Lifecycle.Event.ON_RESUME -> if (paused) {
                    source.lifecycle.removeObserver(this)
                    refreshStatus()
                }
                Lifecycle.Event.ON_DESTROY ->
                    source.lifecycle.removeObserver(this)
                else -> {}
            }
        }
    })
}

@SuppressLint("BatteryLife")
private fun Context.openBatteryOptimizationSettings() {
    val isIgnoring = getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) == true
    val intent = if (isIgnoring)
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    else
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
    startActivity(intent)
}

private fun Context.confirmRestart() =
    showConfirmDialog(
        this,
        title = "설정을 적용하려면 앱을 재시작해야 합니다.",
        message = "지금 앱을 재시작할까요?"
    ) { confirm ->
        if (confirm)
            restartApplication()
    }
