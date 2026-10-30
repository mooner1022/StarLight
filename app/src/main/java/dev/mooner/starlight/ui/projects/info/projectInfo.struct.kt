/*
 * projectInfo.struct.kt created by Minki Moon(mooner1022) on 22. 2. 3. 오후 8:51
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.info

import android.content.Context
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.InfoState
import dev.mooner.configdsl.options.OptionStatus
import dev.mooner.configdsl.options.info
import dev.mooner.configdsl.options.textList
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectImpl
import dev.mooner.starlight.plugincore.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

fun Context.getProjectInfoItems(project: Project) = config {
    val defaultColor = getColor(R.color.main_bright)
    val info = project.info
    val lang = project.getLanguage()
    category {
        id = "general"
        title = "기본"
        textColor = defaultColor
        items {
            info {
                id = "name"
                title = "이름"
                value = info.name
                copyable = true
                icon = Icon.PROJECTS
                iconTintColor = color { "#316B83" }
            }
            info {
                id = "mainScript"
                title = "메인 스크립트"
                value = info.mainScript
                copyable = true
                icon = Icon.EXIT_TO_APP
                iconTintColor = color { "#F4D19B" }
            }
            info {
                id = "birth"
                title = "생성일"
                value = TimeUtils.formatMillis(info.createdMillis, "yyyy/MM/dd HH:mm:ss")
                icon = Icon.ADD
                iconTintColor = color { "#BEAEE2" }
            }
            textList {
                id = "listeners"
                title = "리스너"
                lines = (project as ProjectImpl).allowedEventIDs.sorted()
                monospace = true
                copyable = true
                emptyText = "없음"
                icon = Icon.ARROW_LEFT
                iconTintColor = color { "#98DDCA" }
            }
            textList {
                id = "packages"
                title = "패키지"
                lines = info.packages.toList()
                monospace = true
                copyable = true
                emptyText = "없음"
                icon = Icon.DEVELOPER_BOARD
                iconTintColor = color { "#ff9966" }
            }
        }
    }
    category {
        id = "lang"
        title = "언어"
        textColor = defaultColor
        items {
            info {
                id = "name"
                title = "언어"
                description = lang.id
                value = lang.name
                icon = Icon.DEVELOPER_MODE
                iconTintColor = color { "#4568DC" }
            }
            info {
                id = "fileExt"
                title = "파일 확장자"
                value = lang.fileExtension
                icon = Icon.FOLDER
                iconTintColor = color { "#7A69C7" }
            }
            info {
                id = "reqRelease"
                title = "릴리스 필요"
                value = if (lang.requireRelease) "예" else "아니요"
                icon = Icon.DELETE_SWEEP
                iconTintColor = color { "#B06AB3" }
            }
        }
    }
    category {
        id = "state"
        title = "상태"
        textColor = defaultColor
        items {
            info {
                id = "threadName"
                title = "스레드"
                value = project.threadPoolName ?: "할당되지 않음"
                icon = Icon.LAYERS
                iconTintColor = color { "#3A1C71" }
            }
            info {
                id = "jobs"
                title = "작업"
                icon = Icon.TIMER
                iconTintColor = color { "#57837B" }
                bindValue(every = 1.seconds) { "실행 ${project.activeJobs()} · 대기 ${project.queuedEvents()}" }
            }
            info {
                id = "isCompiled"
                title = "컴파일"
                icon = Icon.REFRESH
                iconTintColor = color { "#D76D77" }
                bind(flow {
                    while (true) {
                        emit(if (project.isCompiled) InfoState("완료", OptionStatus.OK) else InfoState("필요", OptionStatus.WARNING))
                        delay(1.seconds)
                    }
                })
            }
            info {
                id = "isEnabled"
                title = "활성화"
                icon = Icon.EDIT_ATTRIBUTES
                iconTintColor = color { "#FFAF7B" }
                bind(flow {
                    while (true) {
                        emit(if (info.isEnabled) InfoState("켜짐", OptionStatus.OK) else InfoState("꺼짐", null))
                        delay(1.seconds)
                    }
                })
            }
        }
    }
}
