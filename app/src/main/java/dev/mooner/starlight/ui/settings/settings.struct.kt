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
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.mooner.configdsl.ConfigOption
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.RootConfigOption
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.InfoState
import dev.mooner.configdsl.options.OptionStatus
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.info
import dev.mooner.configdsl.options.seekbar
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.CF_LOG_BUFFER_SIZE
import dev.mooner.starlight.CF_LOG_RETENTION
import dev.mooner.starlight.R
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.utils.onSaveConfigAdapter
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
        title = getString(R.string.settings_general_title)
        icon = Icon.SETTINGS
        iconTintColor = color { "#5584AC" }
        items {
            toggle {
                id = "global_power"
                title = getString(R.string.settings_global_power_title)
                description = getString(R.string.settings_global_power_desc)
                icon = Icon.POWER
                iconTintColor = color { "#7ACA8A" }
                defaultValue = true
            }
            info {
                id = OPTION_NOTIFICATION_ACCESS
                title = getString(R.string.settings_notification_access_title)
                description = getString(R.string.settings_notification_access_desc)
                icon = Icon.NOTIFICATIONS_ACTIVE
                val state = requireContext().notificationAccessState()
                value = state.value
                status = state.status
                setOnClickListener { view ->
                    refreshStatusOnReturn(view)
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            info {
                id = OPTION_BATTERY_OPTIMIZATION
                title = getString(R.string.settings_battery_optimization_title)
                description = getString(R.string.settings_battery_optimization_desc)
                icon = Icon.ECO
                val state = requireContext().batteryOptimizationState()
                value = state.value
                status = state.status
                setOnClickListener { view ->
                    refreshStatusOnReturn(view)
                    view.context.openBatteryOptimizationSettings()
                }
            }
            button {
                id = "restart_application"
                title = getString(R.string.settings_restart_application_title)
                description = getString(R.string.settings_restart_application_desc)
                icon = Icon.REFRESH
                iconTintColor = requireContext().getColor(R.color.code_orange)
                setOnClickListener { ->
                    val activity = requireActivity()
                    showConfirmDialog(
                        activity,
                        title = activity.getString(R.string.restart_confirm_title),
                        message = activity.getString(R.string.restart_confirm_message)
                    ) { confirm ->
                        if (confirm)
                            activity.restartApplication()
                    }
                }
            }
        }
    }
    page {
        id = "project_plugin"
        title = getString(R.string.settings_project_plugin_title)
        icon = Icon.PROJECTS
        iconTintColor = color { "#B4CFB0" }
        shareData(GlobalConfig::getMutableData, GlobalConfig::onSaveConfigAdapter)
        structure {
            category {
                id = "project"
                title = getString(R.string.settings_category_project)
                textColor = requireContext().getColor(R.color.main_bright)
                items {
                    toggle {
                        id = "compile_animation"
                        title = getString(R.string.settings_compile_animation_title)
                        description = getString(R.string.settings_compile_animation_desc)
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#FEAC5E" }
                        defaultValue = true
                    }
                    toggle {
                        id = "load_global_libraries"
                        title = getString(R.string.settings_load_global_libraries_title)
                        description = getString(R.string.settings_load_global_libraries_desc)
                        icon = Icon.FOLDER
                        iconTintColor = color { "#4BC0C8" }
                        defaultValue = false
                        warnOnEnable(getString(R.string.settings_load_global_libraries_warning))
                    }
                    seekbar {
                        id = "agent_compile_timeout"
                        title = getString(R.string.settings_agent_compile_timeout_title)
                        description = getString(R.string.settings_agent_compile_timeout_desc)
                        min = 10
                        max = 300
                        defaultValue = 60
                        icon = Icon.TIMER
                        iconTintColor = color { "#C9A7EB" }
                    }
                }
            }
            category {
                id = "plugin"
                title = getString(R.string.settings_category_plugin)
                textColor = requireContext().getColor(R.color.main_bright)
                items {
                    toggle {
                        id = "safe_mode"
                        title = getString(R.string.settings_safe_mode_title)
                        description = getString(R.string.settings_safe_mode_desc)
                        icon = Icon.LAYERS_CLEAR
                        iconTintColor = color { "#95D1CC" }
                        defaultValue = false
                        setOnValueChangedListener { view, _ ->
                            view.context.confirmRestart()
                        }
                    }
                }
            }
        }
    }
    page {
        id = "notifications"
        title = getString(R.string.settings_notifications_title)
        icon = Icon.NOTIFICATIONS
        iconTintColor = color { "#98BAE7" }
        structure {
            category {
                id = "event"
                title = getString(R.string.settings_category_event)
                textColor = requireContext().getColor(R.color.main_bright)
                items {
                    toggle {
                        id = "log_received_message"
                        title = getString(R.string.settings_log_received_message_title)
                        description = getString(R.string.settings_log_received_message_desc)
                        icon = Icon.MARK_CHAT_READ
                        iconTintColor = color { "#7EAA92" }
                        defaultValue = false
                    }
                }
            }
            category {
                id = "noti"
                title = getString(R.string.settings_category_noti)
                textColor = requireContext().getColor(R.color.main_bright)
                items {
                    button {
                        id = "set_package_rules"
                        title = getString(R.string.settings_set_package_rules_title)
                        description = getString(R.string.settings_set_package_rules_desc)
                        icon = Icon.DEVELOPER_BOARD
                        iconTintColor = color { "#7EAA92" }
                        setOnClickListener { _ ->
                            requireActivity().startActivity<NotificationRulesActivity>()
                        }
                    }
                    toggle {
                        id = "use_on_notification_posted"
                        title = getString(R.string.settings_use_on_notification_posted_title)
                        description = getString(R.string.settings_use_on_notification_posted_desc)
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
                    }
                    button {
                        id = "magic"
                        title = getString(R.string.settings_magic_title)
                        description = getString(R.string.settings_magic_desc)
                        icon = Icon.ECO
                        iconTintColor = color { "#FFD9B7" }
                        setOnClickListener { _ ->
                            requireActivity().startConfigActivity(
                                title = getString(R.string.settings_magic_title),
                                subTitle = getString(R.string.settings_magic_subtitle),
                                struct = getProblemSolverStruct(),
                            )
                        }
                    }
                }
            }
        }
    }
    mediaPage(requireContext())
    singleCategoryPage {
        id = "logs"
        title = getString(R.string.settings_logs_title)
        icon = Icon.LIST_BULLETED
        iconTintColor = color { "#F59193" }
        items {
            spinner {
                id = CF_LOG_RETENTION
                title = getString(R.string.settings_log_retention_title)
                description = getString(R.string.settings_log_retention_desc)
                icon = Icon.ARCHIVE
                iconTintColor = color { "#F59193" }
                items = LogCollector.retentionDays.map { if (it == 0) getString(R.string.settings_log_retention_unlimited) else getString(R.string.settings_log_retention_days, it) }
                defaultIndex = LogCollector.DEFAULT_RETENTION_INDEX
            }
            spinner {
                id = CF_LOG_BUFFER_SIZE
                title = getString(R.string.settings_log_buffer_size_title)
                description = getString(R.string.settings_log_buffer_size_desc)
                icon = Icon.LIST_BULLETED
                iconTintColor = color { "#F9AE91" }
                items = LogCollector.bufferSizes.map { getString(R.string.settings_log_buffer_size_count, it) }
                defaultIndex = 0
            }
            button {
                id = OPTION_CLEAR_LOGS
                title = getString(R.string.settings_clear_logs_title)
                description = requireContext().logFilesDescription()
                icon = Icon.DELETE_SWEEP
                iconTintColor = color { "#FF5C58" }
                confirm(getString(R.string.settings_clear_logs_confirm))
                setOnClickListener { _ ->
                    LogCollector.clearLogFiles()
                    refreshStatus()
                }
            }
        }
    }
    category {
        id = "info"
        title = getString(R.string.settings_category_info)
        textColor = requireContext().getColor(R.color.main_bright)
        items {
            // Play builds update only through Google Play.
            if (BuildConfig.SELF_UPDATE)
                button {
                    id = "check_update"
                    title = getString(R.string.settings_check_update_title)
                    icon = Icon.CLOUD_DOWNLOAD
                    iconTintColor = color { "#A7D0CD" }
                    setOnClickListener { _ ->
                        requireActivity().startConfigActivity(CheckUpdateConfigActivity())
                    }
                }
            button {
                id = "app_info"
                title = getString(R.string.settings_app_info_title)
                icon = Icon.INFO
                iconTintColor = color { "#F1CA89" }
                setOnClickListener { _ ->
                    startActivity(Intent(context, AppInfoActivity::class.java))
                }
            }
            button {
                id = "help_dev"
                title = getString(R.string.settings_help_dev_title)
                description = getString(R.string.settings_help_dev_desc)
                icon = Icon.FAVORITE
                iconTintColor = color { "#FF90BC" }
                setOnClickListener { _ ->
                    val intent = Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/mooner".toUri())
                    startActivity(intent)
                }
            }
            if (GlobalConfig.category("dev").getBoolean("dev_mode") == true) {
                button {
                    id = "developer_mode"
                    title = getString(R.string.settings_developer_mode_title)
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

internal fun Context.notificationAccessState(): InfoState =
    if (packageName in NotificationManagerCompat.getEnabledListenerPackages(this))
        InfoState(getString(R.string.settings_notification_access_allowed), OptionStatus.OK)
    else
        InfoState(getString(R.string.settings_notification_access_denied), OptionStatus.WARNING)

internal fun Context.batteryOptimizationState(): InfoState =
    if (getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) == true)
        InfoState(getString(R.string.settings_battery_optimization_exempt), OptionStatus.OK)
    else
        InfoState(getString(R.string.settings_battery_optimization_not_set), OptionStatus.WARNING)

internal fun Context.logFilesDescription(): String {
    val (count, size) = LogCollector.logFileStats()
    return getString(R.string.settings_clear_logs_desc, count, Formatter.formatShortFileSize(this, size))
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
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
    startActivity(intent)
}

private fun Context.confirmRestart() =
    showConfirmDialog(
        this,
        title = getString(R.string.settings_restart_required_title),
        message = getString(R.string.settings_restart_required_message)
    ) { confirm ->
        if (confirm)
            restartApplication()
    }
