package dev.mooner.starlight.ui.plugins.info

import android.content.Context
import android.text.format.Formatter
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.OptionStatus
import dev.mooner.configdsl.options.info
import dev.mooner.configdsl.options.textList
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
import dev.mooner.starlight.ui.plugins.pluginInstaller
import dev.mooner.starlight.ui.plugins.pluginStatus
import dev.mooner.starlight.utils.startConfigActivity
import java.text.DateFormat
import java.util.Date

fun Context.startPluginInfoActivity(
    entry: InstalledPlugin
) {
    val info = entry.info
    val status = pluginStatus(entry, pluginManager.changes[entry.key])
    val none = translate {
        Locale.ENGLISH { "None" }
        Locale.KOREAN  { "없음" }
    }

    val origin = info?.let { pluginInstaller().originOf(it.id) }

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
                info {
                    id = "name"
                    title = info?.name ?: entry.file.name
                    description = info?.id
                    copyable = info != null
                    icon = Icon.LAYERS
                    iconTintColor = color { "#6455A1" }
                }
                if (info != null)
                    info {
                        id = "version"
                        title = translate {
                            Locale.ENGLISH { "Version" }
                            Locale.KOREAN  { "버전" }
                        }
                        icon = Icon.BRANCH
                        iconTintColor = color { "#C073A0" }
                        value = "v${info.version}"
                    }
                info {
                    id = "state"
                    title = translate {
                        Locale.ENGLISH { "Status" }
                        Locale.KOREAN  { "상태" }
                    }
                    icon = if (status.isProblem) Icon.ERROR else Icon.CHECK_CIRCLE
                    iconTintColor = getColor(status.color)
                    val state = entry.state
                    this.status = when {
                        status.isProblem            -> OptionStatus.ERROR
                        state is PluginState.Loaded -> OptionStatus.OK
                        else                        -> null
                    }
                    value = if (state is PluginState.Loaded)
                        translate {
                            Locale.ENGLISH { "Loaded" }
                            Locale.KOREAN  { "로드됨" }
                        }
                    else
                        status.text
                    description = when (state) {
                        is PluginState.Failed  -> state.error.toString()
                        is PluginState.Invalid -> state.error.message ?: state.error.toString()
                        else                   -> null
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
                    info {
                        id = "author"
                        title = translate {
                            Locale.ENGLISH { "Developer(s)" }
                            Locale.KOREAN  { "개발자(들)" }
                        }
                        icon = Icon.DEVELOPER_BOARD
                        iconTintColor = color { "#D47E97" }
                        description = info.authors.joinToString().ifBlank { none }
                    }
                    info {
                        id = "desc"
                        title = translate {
                            Locale.ENGLISH { "Description" }
                            Locale.KOREAN  { "설명" }
                        }
                        description = info.description.ifBlank { none }
                        icon = Icon.LIST_BULLETED
                        iconTintColor = color { "#F59193" }
                    }
                    info {
                        id = "mainClass"
                        title = translate {
                            Locale.ENGLISH { "Main class" }
                            Locale.KOREAN  { "메인 클래스" }
                        }
                        icon = Icon.EXIT_TO_APP
                        iconTintColor = color { "#F9AE91" }
                        description = info.mainClass
                        copyable = true
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
                info {
                    id = "name"
                    title = entry.file.name
                    value = Formatter.formatShortFileSize(this@startPluginInfoActivity, entry.file.length())
                    icon = Icon.FOLDER
                    iconTintColor = color { "#7A69C7" }
                }
                info {
                    id = "origin"
                    title = translate {
                        Locale.ENGLISH { "Source" }
                        Locale.KOREAN  { "출처" }
                    }
                    val repository = origin?.repository
                    icon = if (repository != null) Icon.CLOUD_DOWNLOAD else Icon.FOLDER
                    iconTintColor = color { "#E9B824" }
                    value = repository?.name ?: translate {
                        Locale.ENGLISH { "Local file" }
                        Locale.KOREAN  { "로컬 파일" }
                    }
                    description = buildList {
                        if (repository != null)
                            add(repository.url.removePrefix("https://"))
                        if (origin != null) {
                            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(origin.installedAt))
                            add(translate {
                                Locale.ENGLISH { "Installed $date" }
                                Locale.KOREAN  { "$date 설치" }
                            })
                        }
                    }.joinToString("\n").ifEmpty { null }
                }
                if (info != null)
                    info {
                        id = "dataDir"
                        title = translate {
                            Locale.ENGLISH { "Data folder" }
                            Locale.KOREAN  { "데이터 폴더" }
                        }
                        icon = Icon.ARCHIVE
                        iconTintColor = color { "#4568DC" }
                        description = entry.file.resolveSibling(PluginInstaller.dataDirNameOf(info)).path
                        copyable = true
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
                    info {
                        id = "pluginCoreVersion"
                        title = translate {
                            Locale.ENGLISH { "PluginCore Version" }
                            Locale.KOREAN  { "PluginCore 버전" }
                        }
                        value = info.apiVersion.toString()
                        description = translate {
                            Locale.ENGLISH { "App: ${Info.PLUGINCORE_VERSION}" }
                            Locale.KOREAN  { "앱: ${Info.PLUGINCORE_VERSION}" }
                        }
                        icon = Icon.BRANCH
                        iconTintColor = color { "#3A1C71" }
                    }
                    textList {
                        id = "dependency"
                        title = translate {
                            Locale.ENGLISH { "Required Dependencies" }
                            Locale.KOREAN  { "의존성(필수)" }
                        }
                        icon = Icon.CHECK
                        iconTintColor = color { "#D76D77" }
                        lines = info.dependency.filterNot(PluginDependency::isOptional).describe()
                        emptyText = none
                    }
                    textList {
                        id = "softDependency"
                        title = translate {
                            Locale.ENGLISH { "Soft Dependencies" }
                            Locale.KOREAN  { "의존성(optional)" }
                        }
                        icon = Icon.CHECK
                        iconTintColor = color { "#FFAF7B" }
                        lines = info.softDependency.describe()
                        emptyText = none
                    }
                    info {
                        id = "usesNativeLibrary"
                        title = translate {
                            Locale.ENGLISH { "Uses native libraries" }
                            Locale.KOREAN  { "Native 라이브러리 사용" }
                        }
                        icon = Icon.LINK
                        iconTintColor = color { "#d98366" }
                        value = if (info.usesNativeLibrary)
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

private fun List<PluginDependency>.describe(): List<String> {
    val known = knownPlugins().associateBy { it.id }
    return map { dependency ->
        val found = known[dependency.pluginId]?.let { "v${it.version}" } ?: translate {
            Locale.ENGLISH { "not installed" }
            Locale.KOREAN  { "설치 안 됨" }
        }
        "$dependency · $found"
    }
}
