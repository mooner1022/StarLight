/*
 * CheckUpdateActivity.kt created by Minki Moon(mooner1022) on 8/10/24, 7:48 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.update

import android.content.Context
import androidx.lifecycle.lifecycleScope
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.config
import dev.mooner.configdsl.findOption
import dev.mooner.configdsl.options.ButtonConfigOption
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.utils.onSaveConfigAdapter
import dev.mooner.starlight.ui.config.ConfigActivity
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

class CheckUpdateConfigActivity: dev.mooner.starlight.utils.ConfigActivity() {

    private var updater: AppUpdater? = null
    private var checkUpdateButton: ButtonConfigOption? = null

    override val title: String = "설정"

    override val subTitle: String = "업데이트 확인"

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
                        title = "업데이트 확인"
                        description = activity.lastUpdateCheckDescription()
                        icon = Icon.CLOUD_DOWNLOAD
                        iconTintColor = color("#748DA6")
                        setOnClickListener { _ ->
                            activity.checkUpdate()
                        }
                    }
                }
            }
            category {
                id = "update_config"
                items {
                    toggle {
                        id = "auto_check"
                        title = "업데이트 자동 확인"
                        description = "앱을 시작할 때 새 버전이 있는지 확인하고, 있으면 알려줍니다."
                        icon = Icon.REFRESH
                        iconTintColor = color("#9CB4CC")
                        defaultValue = true
                    }
                    spinner {
                        id = "channel"
                        title = "업데이트 확인 채널"
                        description = """
                            최신 버전을 확인할 채널을 설정합니다.
                            STABLE - 안정 채널
                            BETA - 베타 채널
                            SNAPSHOT - 개발 채널 (불안정)
                        """.trimIndent()
                        icon = Icon.BRANCH
                        iconTintColor = color("#F2D7D9")
                        items = VersionChecker.Channel.entries.map(VersionChecker.Channel::name)
                        defaultIndex = VersionChecker.Channel.STABLE.ordinal
                    }
                    button {
                        val versionName = activity.getPackageInfo().versionName
                        id = "show_changelog"
                        title = "현재 버전 변경 사항"
                        description = "$versionName 변경 사항"
                        icon = Icon.LIST_BULLETED
                        iconTintColor = color("#C1AEFC")
                        setOnClickListener { _ ->
                            activity.showChangelogDialog("✦ $versionName 변경 사항")
                        }
                    }
                }
            }
        }.also { struct ->
            checkUpdateButton = struct.findOption("updates", "check_update") as? ButtonConfigOption
        }
    }

    override fun getSavedData(): MutableDataMap =
        GlobalConfig.getMutableData()

    private fun Context.lastUpdateCheckDescription(): String {
        val lastCheck = lastUpdateCheckMillis()
        val formatted = if (lastCheck == 0L)
            "없음"
        else
            TimeUtils.formatMillis(lastCheck, "yyyy년 MM월 dd일 HH:mm")
        return "마지막 확인: $formatted"
    }

    private fun ConfigActivity.checkUpdate() {
        createSimplePeek(
            text = translate {
                Locale.ENGLISH { "Plz wait for a sec..." }
                Locale.KOREAN  { "서버를 열심히 뒤져보는 중..." }
            }
        ) {
            position = PeekAlert.Position.Bottom
            iconRes = dev.mooner.configdsl.R.drawable.ic_round_cloud_24
            iconTint(res = R.color.main_bright)
            backgroundColor(res = R.color.background_popup)
        }.peek()

        lifecycleScope.launch {
            val result = checkForUpdate()
            when (result) {
                UpdateCheckResult.Failed ->
                    createSimplePeek(
                        text = translate {
                            Locale.ENGLISH { "Failed to parse or fetch version info from server. Please try again later." }
                            Locale.KOREAN  { "서버로부터 버전 정보를 불러오는 데 실패했어요. 나중에 다시 시도해주세요." }
                        }
                    ) {
                        position = PeekAlert.Position.Bottom
                        iconRes = dev.mooner.configdsl.R.drawable.ic_round_close_24
                        iconTint(res = R.color.code_error)
                        backgroundColor(res = R.color.background_popup)
                    }.peek()
                UpdateCheckResult.UpToDate ->
                    createSimplePeek(
                        text = translate {
                            Locale.ENGLISH { "App is already on the newest version." }
                            Locale.KOREAN  { "앱이 이미 최신 버전이에요." }
                        }
                    ) {
                        position = PeekAlert.Position.Bottom
                        iconRes = dev.mooner.configdsl.R.drawable.ic_round_check_24
                        iconTint(res = R.color.noctis_green)
                        backgroundColor(res = R.color.background_popup)
                    }.peek()
                is UpdateCheckResult.Available ->
                    updater?.showUpdateDialog(result)
            }
            if (result != UpdateCheckResult.Failed)
                checkUpdateButton?.updateDescription(lastUpdateCheckDescription())
        }
    }
}
