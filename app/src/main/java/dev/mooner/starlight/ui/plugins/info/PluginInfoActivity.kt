package dev.mooner.starlight.ui.plugins.info

import android.content.Context
import android.text.format.Formatter
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.plugin.InstalledPlugin
import dev.mooner.starlight.plugincore.plugin.PluginDependency
import dev.mooner.starlight.plugincore.plugin.PluginInstaller
import dev.mooner.starlight.plugincore.plugin.PluginState
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.ui.plugins.knownPlugins
import dev.mooner.starlight.ui.plugins.pluginStatus
import dev.mooner.starlight.utils.startConfigActivity

fun Context.startPluginInfoActivity(
    entry: InstalledPlugin
) {
    val info = entry.info
    val status = pluginStatus(entry, pluginManager.changes[entry.key])
    val none = translate {
        Locale.ENGLISH { "None" }
        Locale.KOREAN  { "없음" }
    }

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
                    title = info?.name ?: entry.file.name
                    description = info?.let { "id: ${it.id}" }
                    icon = Icon.LAYERS
                    iconTintColor = color { "#6455A1" }
                }
                if (info != null)
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
                button {
                    id = "state"
                    title = translate {
                        Locale.ENGLISH { "Status" }
                        Locale.KOREAN  { "상태" }
                    }
                    icon = if (status.isProblem) Icon.ERROR else Icon.CHECK_CIRCLE
                    iconTintColor = getColor(status.color)
                    description = when (val state = entry.state) {
                        is PluginState.Loaded -> translate {
                            Locale.ENGLISH { "Loaded" }
                            Locale.KOREAN  { "로드됨" }
                        }
                        is PluginState.Failed -> state.error.toString()
                        is PluginState.Invalid -> "${status.text}\n${state.error.message ?: state.error}"
                        else -> status.text
                    }
                }
            }
        }
        if (info != null)
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
                        description = info.authors.joinToString().ifBlank { none }
                    }
                    button {
                        id = "desc"
                        title = translate {
                            Locale.ENGLISH { "Description" }
                            Locale.KOREAN  { "설명" }
                        }
                        description = info.description.ifBlank { none }
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
                    title = entry.file.name
                    description = translate {
                        Locale.ENGLISH { "Size: ${Formatter.formatShortFileSize(this@startPluginInfoActivity, entry.file.length())}" }
                        Locale.KOREAN  { "크기: ${Formatter.formatShortFileSize(this@startPluginInfoActivity, entry.file.length())}" }
                    }
                    icon = Icon.FOLDER
                    iconTintColor = color { "#7A69C7" }
                }
                if (info != null)
                    button {
                        id = "dataDir"
                        title = translate {
                            Locale.ENGLISH { "Data folder" }
                            Locale.KOREAN  { "데이터 폴더" }
                        }
                        icon = Icon.ARCHIVE
                        iconTintColor = color { "#4568DC" }
                        description = entry.file.resolveSibling(PluginInstaller.dataDirNameOf(info)).path
                    }
            }
        }
        if (info != null)
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
                        description = translate {
                            Locale.ENGLISH { "${info.apiVersion} (app: ${Info.PLUGINCORE_VERSION})" }
                            Locale.KOREAN  { "${info.apiVersion} (앱: ${Info.PLUGINCORE_VERSION})" }
                        }
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
                        description = info.dependency.filterNot(PluginDependency::isOptional).describe(none)
                    }
                    button {
                        id = "softDependency"
                        title = translate {
                            Locale.ENGLISH { "Soft Dependencies" }
                            Locale.KOREAN  { "의존성(optional)" }
                        }
                        icon = Icon.CHECK
                        iconTintColor = color { "#FFAF7B" }
                        description = info.softDependency.describe(none)
                    }
                    button {
                        id = "usesNativeLibrary"
                        title = translate {
                            Locale.ENGLISH { "Uses native libraries" }
                            Locale.KOREAN  { "Native 라이브러리 사용" }
                        }
                        icon = Icon.LINK
                        iconTintColor = color { "#d98366" }
                        description = if (info.usesNativeLibrary)
                            translate {
                                Locale.ENGLISH { "Yes" }
                                Locale.KOREAN  { "예" }
                            }
                        else
                            translate {
                                Locale.ENGLISH { "No" }
                                Locale.KOREAN  { "아니요" }
                            }
                    }
                }
            }
    }

    startConfigActivity(
        title = translate {
            Locale.ENGLISH { "Plugin Info" }
            Locale.KOREAN  { "플러그인 정보" }
        },
        subTitle = info?.name ?: entry.file.name,
        struct = items
    )
}

private fun List<PluginDependency>.describe(none: String): String {
    if (isEmpty())
        return none
    val known = knownPlugins().associateBy { it.id }
    return joinToString("\n") { dependency ->
        val found = known[dependency.pluginId]?.let { "v${it.version}" } ?: translate {
            Locale.ENGLISH { "not installed" }
            Locale.KOREAN  { "설치 안 됨" }
        }
        "$dependency · $found"
    }
}
