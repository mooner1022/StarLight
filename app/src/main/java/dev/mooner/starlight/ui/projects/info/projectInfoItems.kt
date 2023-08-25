/*
 * projectInfoItems.kt created by Minki Moon(mooner1022) on 22. 2. 3. 오후 8:51
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.info

import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectImpl
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.TimeUtils

fun getProjectInfoItems(project: Project) = config {
    val info = project.info
    val lang = project.getLanguage()
    category {
        id = "general"
        title = "기본"
        textColor = color { "#706EB9" }
        items {
            button {
                id = "name"
                title = info.name
                icon = Icon.PROJECTS
                iconTintColor = color { "#316B83" }
            }
            button {
                id = "mainScript"
                title = "메인 스크립트"
                icon = Icon.EXIT_TO_APP
                iconTintColor = color { "#F4D19B" }
                description = info.mainScript
            }
            button {
                id = "birth"
                title = "생성일"
                icon = Icon.ADD
                iconTintColor = color { "#BEAEE2" }
                description = TimeUtils.formatMillis(info.createdMillis, "yyyy/MM/dd HH:mm:ss")
            }
            button {
                id = "listeners"
                title = "리스너"
                icon = Icon.ARROW_LEFT
                iconTintColor = color { "#98DDCA" }
                description = (project as ProjectImpl).allowedEventIDs.joinToString()
            }
            button {
                id = "packages"
                title = "패키지"
                icon = Icon.DEVELOPER_BOARD
                iconTintColor = color { "#ff9966" }
                description = info.packages.joinToString()
            }
        }
    }
    category {
        id = "lang"
        title = "언어"
        textColor = color { "#706EB9" }
        items {
            button {
                id = "name"
                title = lang.name
                description = lang.id
                icon = Icon.DEVELOPER_MODE
                iconTintColor = color { "#4568DC" }
            }
            button {
                id = "fileExt"
                title = "파일 확장자"
                icon = Icon.FOLDER
                iconTintColor = color { "#7A69C7" }
                description = lang.fileExtension
            }
            button {
                id = "reqRelease"
                title = "릴리스 필요"
                icon = Icon.DELETE_SWEEP
                iconTintColor = color { "#B06AB3" }
                description = lang.requireRelease.toString()
            }
        }
    }
    category {
        id = "state"
        title = "상태"
        textColor = color { "#706EB9" }
        items {
            button {
                id = "threadName"
                title = "스레드"
                icon = Icon.LAYERS
                iconTintColor = color { "#3A1C71" }
                description = project.threadPoolName?: "할당되지 않음"
            }
            button {
                id = "isCompiled"
                title = "컴파일"
                icon = Icon.REFRESH
                iconTintColor = color { "#D76D77" }
                description = project.isCompiled.toString()
            }
            button {
                id = "isEnabled"
                title = "활성화"
                icon = Icon.EDIT_ATTRIBUTES
                iconTintColor = color { "#FFAF7B" }
                description = info.isEnabled.toString()
            }
        }
    }
}