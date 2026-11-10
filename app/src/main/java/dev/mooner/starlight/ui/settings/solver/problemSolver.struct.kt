/*
 * problemSolver.struct.kt created by Minki Moon(mooner1022) on 8/4/23, 1:24 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.solver

import android.content.Context
import android.os.Build
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.OptionStatus
import dev.mooner.configdsl.options.info
import dev.mooner.starlight.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.R
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.listener.specs.ParserSpecPolicy
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.ui.settings.notifications.RuleData
import dev.mooner.starlight.ui.settings.notifications.Rules

internal fun getProblemSolverStruct(context: Context = GlobalApplication.requireContext()) = config {
    category {
        id = "state"
        title = context.getString(R.string.solver_category_state)
        items {
            fun item(icon: Icon, title: String, stateBlock: () -> Boolean) {
                info {
                    this.icon = icon
                    this.id = "item_$title"
                    this.title = title
                    val state = stateBlock()
                    status = if (state) OptionStatus.OK else OptionStatus.ERROR
                    value = context.getString(if (state) R.string.solver_on else R.string.solver_off)
                }
            }
            item(Icon.POWER, context.getString(R.string.solver_global_power)) {
                GlobalConfig
                    .category("general")
                    .getBoolean("global_power", true)
            }
            info {
                id = "kakaoTalkVer"
                title = context.getString(R.string.solver_kakaotalk_version)
                val version = ApplicationSession.kakaoTalkVersion?.toString()
                value = version ?: context.getString(R.string.solver_check_failed)
                status = if (version == null) OptionStatus.WARNING else null
                copyable = version != null
                icon = Icon.MARK_CHAT_READ
                iconTintColor = color { "#fae100" }
            }
        }
    }
    category {
        id = "specInfo"
        title = context.getString(R.string.solver_category_rules)
        items {
            // With automatic rules on, the saved rules aren't in use: checking them would report rules nothing reads.
            if (GlobalConfig.category("noti_rules").getBoolean("auto_rule", true)) {
                info {
                    id = "spec_auto"
                    title = context.getString(R.string.rules_auto_title)
                    description = buildString {
                        append(PACKAGE_KAKAO_TALK).append("\n")
                        append(context.getString(R.string.solver_rule_user_id, 0)).append("\n")
                        append(context.getString(R.string.solver_rule_spec, ParserSpecPolicy.specIdFor(Build.VERSION.SDK_INT, ApplicationSession.kakaoTalkVersion)))
                    }
                    icon = Icon.CHECK
                    status = OptionStatus.OK
                    value = context.getString(R.string.solver_ok)
                }
                return@items
            }
            readNotificationRuleFile()
                ?.let(::unwrapData)
                ?.let { list ->
                    if (list.isEmpty())
                        null
                    else
                        list
                }
                ?.mapIndexed { idx, ruleData ->
                    info {
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
                            append(context.getString(R.string.solver_rule_user_id, ruleData.userId)).append("\n")
                            if (ruleData.parserSpecId != ParserSpecPolicy.specIdFor(Build.VERSION.SDK_INT, ApplicationSession.kakaoTalkVersion))
                                markAsError()
                            append(context.getString(R.string.solver_rule_spec, ruleData.parserSpecId))
                        }
                        icon = if (hasError) Icon.ERROR else Icon.CHECK
                        status = if (hasError) OptionStatus.ERROR else OptionStatus.OK
                        value = context.getString(if (hasError) R.string.solver_check_needed else R.string.solver_ok)
                    }
                }
                ?: info {
                    id = "empty"
                    title = context.getString(R.string.solver_no_rules_title)
                    description = context.getString(R.string.solver_no_rules_desc)
                    icon = Icon.ERROR
                    status = OptionStatus.ERROR
                }
        }
    }
}

internal fun readNotificationRuleFile(): String? {
    val file = getStarLightDirectory().resolve(NotificationRulesActivity.FILE_NAME)

    if (!file.exists() || !file.isFile || !file.canRead())
        return null
    return file.readText()
}

internal fun unwrapData(data: String): Rules {
    return data.let<_, List<RuleData>>(Session.json::decodeFromString)
        .toMutableList()
}