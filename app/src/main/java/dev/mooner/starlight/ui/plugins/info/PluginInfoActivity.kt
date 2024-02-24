package dev.mooner.starlight.ui.plugins.info

import android.content.Context
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.utils.startConfigActivity

fun Context.startPluginInfoActivity(
    plugin: StarlightPlugin
) {
    val info = plugin.info

    val items = config {
        val defaultColor = getColor(R.color.main_bright)
        category {
            id = "general"
            title = translate {
                Locale.ENGLISH { "General" }
                Locale.KOREAN  { "기본" }
            }
            textColor = defaultColor
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
                    title = translate {
                        Locale.ENGLISH { "Version" }
                        Locale.KOREAN  { "버전" }
                    }
                    icon = Icon.BRANCH
                    iconTintColor = color { "#C073A0" }
                    description = "v${info.version}"
                }
            }
        }
        category {
            id = "info"
            title = translate {
                Locale.ENGLISH { "Information" }
                Locale.KOREAN  { "등록 정보" }
            }
            textColor = defaultColor
            items {
                button {
                    id = "author"
                    title = translate {
                        Locale.ENGLISH { "Developer(s)" }
                        Locale.KOREAN  { "개발자(들)" }
                    }
                    icon = Icon.DEVELOPER_BOARD
                    iconTintColor = color { "#D47E97" }
                    description = info.authors.joinToString()
                }
                button {
                    id = "desc"
                    title = translate {
                        Locale.ENGLISH { "Description" }
                        Locale.KOREAN  { "설명" }
                    }
                    description = info.description
                    icon = Icon.LIST_BULLETED
                    iconTintColor = color { "#F59193" }
                }
                button {
                    id = "mainClass"
                    title = translate {
                        Locale.ENGLISH { "Main class" }
                        Locale.KOREAN  { "메인 클래스" }
                    }
                    icon = Icon.EXIT_TO_APP
                    iconTintColor = color { "#F9AE91" }
                    description = info.mainClass
                }
            }
        }
        category {
            id = "file"
            title = translate {
                Locale.ENGLISH { "File" }
                Locale.KOREAN  { "파일" }
            }
            textColor = defaultColor
            items {
                button {
                    id = "name"
                    title = plugin.fileName
                    icon = Icon.FOLDER
                    iconTintColor = color { "#7A69C7" }
                }
                button {
                    id = "size"
                    title = translate {
                        Locale.ENGLISH { "Size" }
                        Locale.KOREAN  { "크기(용량)" }
                    }
                    icon = Icon.COMPRESS
                    iconTintColor = color { "#4568DC" }
                    description = "${plugin.fileSize}mb"
                }
            }
        }
        category {
            id = "library"
            title = translate {
                Locale.ENGLISH { "Library" }
                Locale.KOREAN  { "라이브러리" }
            }
            textColor = defaultColor
            items {
                button {
                    id = "pluginCoreVersion"
                    title = translate {
                        Locale.ENGLISH { "PluginCore Version" }
                        Locale.KOREAN  { "PluginCore 버전" }
                    }
                    description = info.apiVersion.toString()
                    icon = Icon.BRANCH
                    iconTintColor = color { "#3A1C71" }
                }
                button {
                    id = "dependency"
                    title = translate {
                        Locale.ENGLISH { "Required Dependencies" }
                        Locale.KOREAN  { "의존성(필수)" }
                    }
                    icon = Icon.CHECK
                    iconTintColor = color { "#D76D77" }
                    description = if (info.dependency.isEmpty()) "없음" else info.dependency.joinToString("\n")
                }
                button {
                    id = "softDependency"
                    title = translate {
                        Locale.ENGLISH { "Soft Dependencies" }
                        Locale.KOREAN  { "의존성(optional)" }
                    }
                    icon = Icon.CHECK
                    iconTintColor = color { "#FFAF7B" }
                    description = if (info.softDependency.isEmpty()) "없음" else info.softDependency.joinToString("\n")
                }
                button {
                    id = "usesNativeLibrary"
                    title = translate {
                        Locale.ENGLISH { "Uses native libraries" }
                        Locale.KOREAN  { "Native 라이브러리 사용" }
                    }
                    icon = Icon.LINK
                    iconTintColor = color { "#d98366" }
                    description = info.usesNativeLibrary.toString()
                }
            }
        }
    }

    startConfigActivity(
        title = translate {
            Locale.ENGLISH { "Plugin Info" }
            Locale.KOREAN  { "플러그인 정보" }
        },
        subTitle = info.name,
        struct = items
    )
}