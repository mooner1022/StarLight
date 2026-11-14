/*
 * CheckUpdateActivity.kt created by Minki Moon(mooner1022) on 8/10/24, 7:48 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.update

import android.content.Context
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.utils.onSaveConfigAdapter
import dev.mooner.starlight.ui.config.ConfigActivity
import dev.mooner.starlight.utils.*
import kotlinx.serialization.json.JsonElement

class CheckUpdateConfigActivity: dev.mooner.starlight.utils.ConfigActivity() {

    private var updater: AppUpdater? = null

    override val title: String
        get() = GlobalApplication.requireContext().getString(R.string.title_settings)

    override val subTitle: String
        get() = GlobalApplication.requireContext().getString(R.string.update_check_title)

    override fun onCreated(activity: ConfigActivity) {
        updater = AppUpdater(activity)
    }

    override suspend fun onValueUpdated(
        parentId: String,
        id: String,
        value: Any,
        jsonValue: JsonElement
    ) {
        GlobalConfig.onSaveConfigAdapter(parentId, id, value, jsonValue)
    }

    override fun onDestroyed() {
        updater = null
    }

    override fun getStructBlock(activity: ConfigActivity): ConfigStructure {
        return config {
            category {
                id = "updates"
                items {
                    button {
                        id = "check_update"
                        title = activity.getString(R.string.update_check_title)
                        description = activity.lastUpdateCheckDescription()
                        icon = Icon.CLOUD_DOWNLOAD
                        iconTintColor = color("#748DA6")
                        action {
                            if (activity.checkUpdate())
                                setDescription(activity.lastUpdateCheckDescription())
                        }
                    }
                }
            }
            category {
                id = "update_config"
                items {
                    toggle {
                        id = "auto_check"
                        title = activity.getString(R.string.update_auto_title)
                        description = activity.getString(R.string.update_auto_desc)
                        icon = Icon.REFRESH
                        iconTintColor = color("#9CB4CC")
                        defaultValue = true
                    }
                    spinner {
                        id = "channel"
                        title = activity.getString(R.string.update_channel_title)
                        description = activity.getString(R.string.update_channel_desc)
                        icon = Icon.BRANCH
                        iconTintColor = color("#F2D7D9")
                        items = VersionChecker.Channel.entries.map(VersionChecker.Channel::name)
                        defaultIndex = VersionChecker.Channel.STABLE.ordinal
                    }
                    button {
                        val versionName = activity.getPackageInfo().versionName
                        id = "show_changelog"
                        title = activity.getString(R.string.update_changes_title)
                        description = activity.getString(R.string.update_changes_desc, versionName)
                        icon = Icon.LIST_BULLETED
                        iconTintColor = color("#C1AEFC")
                        setOnClickListener { _ ->
                            activity.showChangelogDialog(activity.getString(R.string.update_changes_dialog_title, versionName))
                        }
                    }
                }
            }
        }
    }

    override fun getSavedData(): MutableDataMap =
        GlobalConfig.getMutableData()

    private fun Context.lastUpdateCheckDescription(): String {
        val lastCheck = lastUpdateCheckMillis()
        val formatted = if (lastCheck == 0L)
            getString(R.string.update_last_check_never)
        else
            TimeUtils.formatMillis(lastCheck, getString(R.string.update_last_check_pattern))
        return getString(R.string.update_last_check, formatted)
    }

    private suspend fun ConfigActivity.checkUpdate(): Boolean {
        val result = checkForUpdate()
        when (result) {
            UpdateCheckResult.Failed ->
                createFailurePeek(translate {
                    Locale.ENGLISH { "Failed to parse or fetch version info from server. Please try again later." }
                    Locale.KOREAN  { "서버로부터 버전 정보를 불러오는 데 실패했어요. 나중에 다시 시도해주세요." }
                }, PeekAlert.Position.Bottom).peek()
            UpdateCheckResult.UpToDate ->
                createSuccessPeek(translate {
                    Locale.ENGLISH { "App is already on the newest version." }
                    Locale.KOREAN  { "앱이 이미 최신 버전이에요." }
                }, PeekAlert.Position.Bottom).peek()
            is UpdateCheckResult.Available ->
                updater?.showUpdateDialog(result)
        }
        return result != UpdateCheckResult.Failed
    }
}
