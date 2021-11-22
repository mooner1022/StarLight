package com.mooner.starlight.utils

import android.content.Context
import android.content.Intent
import android.view.View
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.core.Session.globalConfig
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.models.TypedString
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.plugincore.utils.TimeUtils
import com.mooner.starlight.ui.config.ConfigActivity
import com.mooner.starlight.ui.config.ParentAdapter
import java.util.*

private const val T = "ConfigUtils"
const val EXTRA_TITLE = "title"
const val EXTRA_SUBTITLE = "subTitle"
const val EXTRA_ACTIVITY_ID = "activityId"

private data class DataHolder(
    val items: List<CategoryConfigObject>,
    val saved: Map<String, Map<String, TypedString>>,
    val listener: (parentId: String, id: String, view: View, data: Any) -> Unit,
    val onDestroy: () -> Unit,
    var instance: ConfigActivity? = null
)

private val holders: MutableMap<String, DataHolder> = hashMapOf()

fun Context.startConfigActivity(
    id: String? = null,
    title: String,
    subTitle: String,
    items: List<CategoryConfigObject>,
    saved: Map<String, Map<String, TypedString>> = mapOf(),
    onConfigChanged: (parentId: String, id: String, view: View, data: Any) -> Unit = { _, _, _, _ -> },
    onDestroy: () -> Unit = {}
) {
    val activityId = id ?: UUID.randomUUID().toString()
    holders[activityId] = DataHolder(items, saved, onConfigChanged, onDestroy)
    val intent = Intent(this, ConfigActivity::class.java).apply {
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_SUBTITLE, subTitle)
        putExtra(EXTRA_ACTIVITY_ID, activityId)
    }
    startActivity(intent)
    Logger.v(T, "Starting new config activity with ID: $activityId")
}

fun finishConfigActivity(id: String): Boolean {
    if (id in holders) {
        val holder = holders[id]!!
        if (holder.instance != null) {
            Logger.v(T, "Finishing config activity with ID: $id")
            holder.instance!!.apply {
                onDestroyed()
                finish()
            }
            return true
        }
    }
    return false
}

internal fun ConfigActivity.initAdapter() {
    val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    val holder = holders[activityId]?: error("Failed to find holder with id $activityId")
    recyclerAdapter = ParentAdapter(
        context = this,
        onConfigChanged = holder.listener
    ).apply {
        data = holder.items
        saved = holder.saved
        notifyDataSetChanged()
    }
    holder.instance = this
}

internal fun ConfigActivity.onDestroyed() {
    val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    if (holders.containsKey(activityId)) {
        holders[activityId]!!.onDestroy()
        holders -= activityId
        Logger.v(T, "onDestroyed() called from config activity with ID: $activityId")
    }
}

fun Context.startDevModeActivity() {
    val activityId = UUID.randomUUID().toString()

    startConfigActivity(
        id = activityId,
        title = "설정",
        subTitle = "개발자 모드",
        saved = globalConfig.getAllConfigs(),
        items = config {
            category {
                id = "dev_mode_config"
                title = "개발자 모드"
                textColor = color { "#706EB9" }
                items = items {
                    toggle {
                        id = "show_internal_log"
                        title = "내부 로그 표시"
                        description = "앱 내부의 디버깅용 로그를 표시합니다."
                        icon = Icon.MARK_CHAT_UNREAD
                        iconTintColor = color { "#87AAAA" }
                        defaultValue = false
                    }
                    button {
                        id = "make_error"
                        title = "에러 발생"
                        description = "고의적으로 에러를 발생시킵니다."
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF5C58" }
                        onClickListener = {
                            throw Exception("Expected error created from dev mode")
                        }
                    }
                    button {
                        id = "disable_dev_mode"
                        title = "개발자 모드 비활성화"
                        icon = Icon.DEVELOPER_BOARD_OFF
                        iconTintColor = color { "#FF8243" }
                        onClickListener = {
                            globalConfig.edit {
                                getCategory("dev")["dev_mode"] = false
                            }
                            finishConfigActivity(activityId)
                        }
                    }
                }
            }
        },
        onConfigChanged = { parentId, id, _, data ->
            globalConfig.edit {
                getCategory(parentId)[id] = data
            }
        }
    )
}

fun Context.startProjectInfoActivity(
    project: Project
) {
    val info = project.info
    val lang = project.getLanguage()

    val items = config {
        category {
            id = "general"
            title = "기본"
            textColor = color { "#706EB9" }
            items = items {
                button {
                    id = "name"
                    title = info.name
                    icon = Icon.PROJECTS
                    iconTintColor = color { "#316B83" }
                    onClickListener = {}
                }
                button {
                    id = "mainScript"
                    title = "메인 스크립트"
                    icon = Icon.EXIT_TO_APP
                    iconTintColor = color { "#F4D19B" }
                    description = info.mainScript
                    onClickListener = {}
                }
                button {
                    id = "birth"
                    title = "생성일"
                    icon = Icon.ADD
                    iconTintColor = color { "#BEAEE2" }
                    description = TimeUtils.formatMillis(info.createdMillis, "yyyy/MM/dd HH:mm:ss")
                    onClickListener = {}
                }
                button {
                    id = "listeners"
                    title = "리스너"
                    icon = Icon.ARROW_LEFT
                    iconTintColor = color { "#98DDCA" }
                    description = info.listeners.joinToString()
                    onClickListener = {}
                }
                button {
                    id = "plugins"
                    title = "플러그인"
                    icon = Icon.LAYERS
                    iconTintColor = color { "#70AF85" }
                    description = info.pluginIds.joinToString()
                    onClickListener = {}
                }
                button {
                    id = "packages"
                    title = "패키지"
                    icon = Icon.DEVELOPER_BOARD
                    iconTintColor = color { "#ff9966" }
                    description = info.packages.joinToString()
                    onClickListener = {}
                }
            }
        }
        category {
            id = "lang"
            title = "언어"
            textColor = color { "#706EB9" }
            items = items {
                button {
                    id = "name"
                    title = lang.name
                    description = lang.id
                    icon = Icon.DEVELOPER_MODE
                    iconTintColor = color { "#4568DC" }
                    onClickListener = {}
                }
                button {
                    id = "fileExt"
                    title = "파일 확장자"
                    icon = Icon.FOLDER
                    iconTintColor = color { "#7A69C7" }
                    description = lang.fileExtension
                    onClickListener = {}
                }
                button {
                    id = "reqRelease"
                    title = "릴리스 필요"
                    icon = Icon.DELETE_SWEEP
                    iconTintColor = color { "#B06AB3" }
                    description = lang.requireRelease.toString()
                    onClickListener = {}
                }
            }
        }
        category {
            id = "state"
            title = "상태"
            textColor = color { "#706EB9" }
            items = items {
                button {
                    id = "threadName"
                    title = "스레드"
                    icon = Icon.LAYERS
                    iconTintColor = color { "#3A1C71" }
                    description = project.threadName?: "할당되지 않음"
                    onClickListener = {}
                }
                button {
                    id = "isCompiled"
                    title = "컴파일"
                    icon = Icon.REFRESH
                    iconTintColor = color { "#D76D77" }
                    description = project.isCompiled.toString()
                    onClickListener = {}
                }
                button {
                    id = "isEnabled"
                    title = "활성화"
                    icon = Icon.EDIT_ATTRIBUTES
                    iconTintColor = color { "#FFAF7B" }
                    description = info.isEnabled.toString()
                    onClickListener = {}
                }
            }
        }
    }

    startConfigActivity(
        title = "정보",
        subTitle = info.name,
        items = items
    )
}

fun Context.startPluginInfoActivity(
    plugin: StarlightPlugin
) {
    val info = plugin.info

    val items = config {
        category {
            id = "general"
            title = "기본"
            textColor = color { "#706EB9" }
            items = items {
                button {
                    id = "name"
                    title = plugin.name
                    description = "id: ${info.id}"
                    icon = Icon.LAYERS
                    iconTintColor = color { "#6455A1" }
                    onClickListener = {}
                }
                button {
                    id = "version"
                    title = "버전"
                    icon = Icon.BRANCH
                    iconTintColor = color { "#C073A0" }
                    description = "v${info.version}"
                    onClickListener = {}
                }
            }
        }
        category {
            id = "info"
            title = "등록 정보"
            textColor = color { "#706EB9" }
            items = items {
                button {
                    id = "author"
                    title = "개발자"
                    icon = Icon.DEVELOPER_BOARD
                    iconTintColor = color { "#D47E97" }
                    description = info.authors.joinToString()
                    onClickListener = {}
                }
                button {
                    id = "desc"
                    title = "설명"
                    description = info.description
                    icon = Icon.LIST_BULLETED
                    iconTintColor = color { "#F59193" }
                    onClickListener = {}
                }
                button {
                    id = "mainClass"
                    title = "메인 클래스"
                    icon = Icon.EXIT_TO_APP
                    iconTintColor = color { "#F9AE91" }
                    description = info.mainClass
                    onClickListener = {}
                }
            }
        }
        category {
            id = "file"
            title = "파일"
            textColor = color { "#706EB9" }
            items = items {
                button {
                    id = "name"
                    title = plugin.fileName
                    icon = Icon.FOLDER
                    iconTintColor = color { "#7A69C7" }
                    onClickListener = {}
                }
                button {
                    id = "size"
                    title = "크기"
                    icon = Icon.COMPRESS
                    iconTintColor = color { "#4568DC" }
                    description = "${plugin.fileSize}mb"
                    onClickListener = {}
                }
            }
        }
        category {
            id = "library"
            title = "라이브러리"
            textColor = color { "#706EB9" }
            items = items {
                button {
                    id = "pluginCoreVersion"
                    title = "PluginCore 버전"
                    description = info.apiVersion
                    icon = Icon.BRANCH
                    iconTintColor = color { "#3A1C71" }
                    onClickListener = {}
                }
                button {
                    id = "dependency"
                    title = "의존성(필수)"
                    icon = Icon.CHECK
                    iconTintColor = color { "#D76D77" }
                    description = if (info.depend.isEmpty()) "없음" else info.depend.joinToString("\n")
                    onClickListener = {}
                }
                button {
                    id = "softDependency"
                    title = "의존성(soft)"
                    icon = Icon.CHECK
                    iconTintColor = color { "#FFAF7B" }
                    description = if (info.softDepend.isEmpty()) "없음" else info.softDepend.joinToString("\n")
                    onClickListener = {}
                }
            }
        }
    }

    startConfigActivity(
        title = "정보",
        subTitle = info.name,
        items = items
    )
}