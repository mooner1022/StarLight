package dev.mooner.starlight.ui.plugins.info

import android.content.Context
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.utils.startConfigActivity

fun Context.startPluginInfoActivity(
    plugin: StarlightPlugin
) {
    val info = plugin.info

    val items = config {
        category {
            id = "general"
            title = "기본"
            textColor = color { "#706EB9" }
            items {
                button {
                    id = "name"
                    title = plugin.info.name
                    description = "id: ${info.id}"
                    icon = Icon.LAYERS
                    iconTintColor = color { "#6455A1" }
                }
                button {
                    id = "version"
                    title = "버전"
                    icon = Icon.BRANCH
                    iconTintColor = color { "#C073A0" }
                    description = "v${info.version}"
                }
            }
        }
        category {
            id = "info"
            title = "등록 정보"
            textColor = color { "#706EB9" }
            items {
                button {
                    id = "author"
                    title = "개발자"
                    icon = Icon.DEVELOPER_BOARD
                    iconTintColor = color { "#D47E97" }
                    description = info.authors.joinToString()
                }
                button {
                    id = "desc"
                    title = "설명"
                    description = info.description
                    icon = Icon.LIST_BULLETED
                    iconTintColor = color { "#F59193" }
                }
                button {
                    id = "mainClass"
                    title = "메인 클래스"
                    icon = Icon.EXIT_TO_APP
                    iconTintColor = color { "#F9AE91" }
                    description = info.mainClass
                }
            }
        }
        category {
            id = "file"
            title = "파일"
            textColor = color { "#706EB9" }
            items {
                button {
                    id = "name"
                    title = plugin.fileName
                    icon = Icon.FOLDER
                    iconTintColor = color { "#7A69C7" }
                }
                button {
                    id = "size"
                    title = "크기"
                    icon = Icon.COMPRESS
                    iconTintColor = color { "#4568DC" }
                    description = "${plugin.fileSize}mb"
                }
            }
        }
        category {
            id = "library"
            title = "라이브러리"
            textColor = color { "#706EB9" }
            items {
                button {
                    id = "pluginCoreVersion"
                    title = "PluginCore 버전"
                    description = info.apiVersion.toString()
                    icon = Icon.BRANCH
                    iconTintColor = color { "#3A1C71" }
                }
                button {
                    id = "dependency"
                    title = "의존성(필수)"
                    icon = Icon.CHECK
                    iconTintColor = color { "#D76D77" }
                    description = if (info.dependency.isEmpty()) "없음" else info.dependency.joinToString("\n")
                }
                button {
                    id = "softDependency"
                    title = "의존성(soft)"
                    icon = Icon.CHECK
                    iconTintColor = color { "#FFAF7B" }
                    description = if (info.softDependency.isEmpty()) "없음" else info.softDependency.joinToString("\n")
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