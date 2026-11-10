/*
 * projectInfo.struct.kt created by Minki Moon(mooner1022) on 22. 2. 3. 오후 8:51
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.info

import android.content.Context
import androidx.activity.ComponentActivity
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.InfoState
import dev.mooner.configdsl.options.OptionStatus
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.info
import dev.mooner.configdsl.options.textList
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectImpl
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.ui.projects.exportProject
import dev.mooner.starlight.utils.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

fun Context.getProjectInfoItems(project: Project) = config {
    val defaultColor = getColor(R.color.text_gold)
    val info = project.info
    val lang = project.getLanguage()
    category {
        id = "general"
        title = getString(R.string.project_info_general_category)
        textColor = defaultColor
        items {
            info {
                id = "name"
                title = getString(R.string.project_info_general_name_title)
                value = info.name
                copyable = true
                icon = Icon.PROJECTS
                iconTintColor = color { "#316B83" }
            }
            info {
                id = "mainScript"
                title = getString(R.string.project_info_main_script_title)
                value = info.mainScript
                copyable = true
                icon = Icon.EXIT_TO_APP
                iconTintColor = color { "#F4D19B" }
            }
            info {
                id = "birth"
                title = getString(R.string.project_info_birth_title)
                value = TimeUtils.formatMillis(info.createdMillis, "yyyy/MM/dd HH:mm:ss")
                icon = Icon.ADD
                iconTintColor = color { "#BEAEE2" }
            }
            textList {
                id = "listeners"
                title = getString(R.string.project_info_listeners_title)
                lines = (project as ProjectImpl).allowedEventIDs.sorted()
                monospace = true
                copyable = true
                emptyText = getString(R.string.project_info_listeners_empty)
                icon = Icon.ARROW_LEFT
                iconTintColor = color { "#98DDCA" }
            }
            textList {
                id = "packages"
                title = getString(R.string.project_info_packages_title)
                lines = info.packages.toList()
                monospace = true
                copyable = true
                emptyText = getString(R.string.project_info_packages_empty)
                icon = Icon.DEVELOPER_BOARD
                iconTintColor = color { "#ff9966" }
            }
        }
    }
    category {
        id = "lang"
        title = getString(R.string.project_info_lang_category)
        textColor = defaultColor
        items {
            info {
                id = "name"
                title = getString(R.string.project_info_lang_name_title)
                description = lang.id
                value = lang.name
                icon = Icon.DEVELOPER_MODE
                iconTintColor = color { "#4568DC" }
            }
            info {
                id = "fileExt"
                title = getString(R.string.project_info_file_ext_title)
                value = lang.fileExtension
                icon = Icon.FOLDER
                iconTintColor = color { "#7A69C7" }
            }
            info {
                id = "reqRelease"
                title = getString(R.string.project_info_req_release_title)
                value = getString(if (lang.requireRelease) R.string.project_info_req_release_yes else R.string.project_info_req_release_no)
                icon = Icon.DELETE_SWEEP
                iconTintColor = color { "#B06AB3" }
            }
        }
    }
    category {
        id = "state"
        title = getString(R.string.project_info_state_category)
        textColor = defaultColor
        items {
            info {
                id = "threadName"
                title = getString(R.string.project_info_thread_name_title)
                value = project.threadPoolName ?: getString(R.string.project_info_thread_name_none)
                icon = Icon.LAYERS
                iconTintColor = color { "#3A1C71" }
            }
            info {
                id = "jobs"
                title = getString(R.string.project_info_jobs_title)
                icon = Icon.TIMER
                iconTintColor = color { "#57837B" }
                bindValue(every = 1.seconds) { getString(R.string.project_info_jobs_value, project.activeJobs(), project.queuedEvents()) }
            }
            info {
                id = "isCompiled"
                title = getString(R.string.project_info_is_compiled_title)
                icon = Icon.REFRESH
                iconTintColor = color { "#D76D77" }
                bind(flow {
                    while (true) {
                        emit(if (project.isCompiled) InfoState(getString(R.string.project_info_is_compiled_done), OptionStatus.OK) else InfoState(getString(R.string.project_info_is_compiled_needed), OptionStatus.WARNING))
                        delay(1.seconds)
                    }
                })
            }
            info {
                id = "isEnabled"
                title = getString(R.string.project_info_is_enabled_title)
                icon = Icon.EDIT_ATTRIBUTES
                iconTintColor = color { "#FFAF7B" }
                bind(flow {
                    while (true) {
                        emit(if (info.isEnabled) InfoState(getString(R.string.project_info_is_enabled_on), OptionStatus.OK) else InfoState(getString(R.string.project_info_is_enabled_off), null))
                        delay(1.seconds)
                    }
                })
            }
        }
    }
    category {
        id = "file"
        title = getString(R.string.project_info_file_category)
        textColor = defaultColor
        items {
            button {
                id = "export"
                title = getString(R.string.project_info_export_title)
                description = getString(R.string.project_info_export_desc)
                icon = Icon.ARCHIVE
                iconTintColor = color { "#4568DC" }
                action {
                    (context.findActivity() as? ComponentActivity)?.exportProject(project)
                }
            }
        }
    }
}
