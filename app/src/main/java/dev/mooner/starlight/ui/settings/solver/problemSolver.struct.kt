/*
 * problemSolver.struct.kt created by Minki Moon(mooner1022) on 8/4/23, 1:24 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.solver

import android.os.Build
import androidx.fragment.app.Fragment
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.starlight.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.ui.settings.notifications.RuleData
import dev.mooner.starlight.ui.settings.notifications.Rules

context(Fragment)
internal fun getProblemSolverStruct() = config {
    val colorPrimary = color { "#7ACA8A" }
    val colorError   = color { "#FF6188" }
    fun stateColor(state: Boolean): Int =
        if (state) colorPrimary else colorError
    category {
        id = "state"
        title = "변수 / 상태"
        items {
            fun item(icon: Icon, title: String, stateBlock: () -> Boolean) {
                button {
                    this.icon = icon
                    this.id = "item_$title"
                    this.title = title
                    val state = stateBlock()
                    this.iconTintColor = stateColor(state)
                    this.description = if (state) "켜짐" else "❗ 값이 비활성화 되어 있습니다"
                }
            }
            item(Icon.POWER, "전역 전원") {
                GlobalConfig
                    .category("general")
                    .getBoolean("global_power", true)
            }
            item(Icon.BOOKMARK, "레거시 호환 이벤트") {
                GlobalConfig
                    .category("notifications")
                    .getBoolean("use_legacy_event", false)
            }
            button { 
                id = "kakaoTalkVer"
                title = "카카오톡 버전"
                description = ApplicationSession.kakaoTalkVersion?.toString() ?: "확인 실패(com.kakao.talk)"
                icon = Icon.MARK_CHAT_READ
                iconTintColor = color { "#fae100" }
            }
        }
    }
    category {
        id = "specInfo"
        title = "패키지 규칙 검사"
        items {
            readNotificationRuleFile()
                ?.let(::unwrapData)
                ?.let { list ->
                    if (list.isEmpty())
                        null
                    else
                        list
                }
                ?.mapIndexed { idx, ruleData ->
                    button {
                        var hasError = false
                        id = "spec_$idx"
                        title = "$idx : ${ruleData.packageName}"
                        description = buildString {
                            fun markAsError() {
                                append("❗ ")
                                hasError = true
                            }
                            if (ruleData.packageName != PACKAGE_KAKAO_TALK)
                                markAsError()
                            append(ruleData.packageName).append("\n")
                            if (ruleData.userId != 0)
                                markAsError()
                            append("유저 ID : ").append(ruleData.userId).append("\n")
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ruleData.parserSpecId == "android_r")
                                markAsError()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ruleData.parserSpecId == "default")
                                markAsError()
                            append("파싱 스펙 : ${ruleData.parserSpecId}")
                        }
                        icon = if (hasError) Icon.ERROR else Icon.CHECK
                        iconTintColor = stateColor(!hasError)
                    }
                }
                ?: button {
                    id = "empty"
                    title = "❗ 패키지 규칙이 존재하지 않아요."
                    description = "패키지 규칙 없이는 알림이 분석되거나 이벤트가 발생하지 않아요. 올바른 규칙을 추가해주세요."
                    icon = Icon.ERROR
                    iconTintColor = colorError
                }
        }
    }
}

private fun readNotificationRuleFile(): String? {
    val file = getStarLightDirectory().resolve(NotificationRulesActivity.FILE_NAME)

    if (!file.exists() || !file.isFile || !file.canRead())
        return null
    return file.readText()
}

private fun unwrapData(data: String): Rules {
    return data.let<_, List<RuleData>>(Session.json::decodeFromString)
        .toMutableList()
}