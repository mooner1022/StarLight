package dev.mooner.starlight.ui.widget

import androidx.lifecycle.Lifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.stats.LogStats
import dev.mooner.starlight.utils.showLogsDialog
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

const val WIDGET_ERRORS = "widget_errors"

val ErrorMonitorWidget = widget(WIDGET_ERRORS) {
    name {
        Locale.ENGLISH { "Error monitor" }
        Locale.KOREAN  { "오류 모니터" }
    }
    span(default = 4 x 2, min = 2 x 1, max = 8 x 6)

    content {
        val binding = panel(R.drawable.ic_round_error_outline_24, R.string.widget_errors)
        binding.root.setClickAction { it.context.showLogsDialog() }

        fun render() {
            val errors = LogStats.errorCount
            val warnings = LogStats.warningCount
            val colorRes = when {
                errors > 0   -> R.color.status_error
                warnings > 0 -> R.color.status_warning
                else         -> R.color.status_ok
            }
            binding.setSummary(translate {
                Locale.ENGLISH { "Errors $errors · Warnings $warnings" }
                Locale.KOREAN  { "오류 $errors · 경고 $warnings" }
            }, colorRes)
            if (isCompact)
                return

            val recent = latestProjectErrors().take(rowCapacity())
            if (recent.isEmpty()) {
                binding.setContent(listOf(createMessage(translate {
                    Locale.ENGLISH { "No project errors yet" }
                    Locale.KOREAN  { "아직 프로젝트 오류가 없어요" }
                })))
                return
            }
            binding.setContent(recent.map { (project, log) ->
                createRow(
                    title = "${project.info.name}: ${log.message.lineSequence().first()}",
                    value = TimeUtils.formatMillis(log.millis, "HH:mm"),
                    valueColorRes = R.color.text_sub,
                    icon = R.drawable.ic_round_error_outline_24,
                    iconTintRes = R.color.status_error,
                    fill = false,
                )
            })
        }

        render()
        repeatWhile(Lifecycle.State.STARTED) {
            render()
            LogStats.updates.collect {
                render()
                delay(RENDER_INTERVAL)
            }
        }
    }
}

private fun latestProjectErrors(): List<Pair<Project, LogData>> {
    if (!Session.isInitComplete)
        return emptyList()
    return Session.projectManager.getProjects()
        .mapNotNull { project ->
            val logs = project.logger.logs
            synchronized(logs) { logs.lastOrNull { it.isError && it.millis >= LogStats.startedMillis } }
                ?.let { project to it }
        }
        .sortedByDescending { (_, log) -> log.millis }
}

private val LogData.isError: Boolean
    get() = type == LogType.ERROR || type == LogType.CRITICAL

private val RENDER_INTERVAL = 500.milliseconds
