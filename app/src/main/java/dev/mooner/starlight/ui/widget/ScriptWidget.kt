package dev.mooner.starlight.ui.widget

import androidx.lifecycle.Lifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.stats.ScriptWidgetEntries
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

const val WIDGET_SCRIPT = "widget_script"

val ScriptWidget = widget(WIDGET_SCRIPT) {
    name {
        Locale.ENGLISH { "Script values" }
        Locale.KOREAN  { "스크립트 값" }
    }
    span(default = 4 x 2, min = 2 x 1, max = 8 x 6)

    content {
        val binding = panel(R.drawable.ic_round_code_24, R.string.widget_script)

        fun render() {
            val entries = ScriptWidgetEntries.all()
            val latest = entries.firstOrNull()
            if (latest == null) {
                binding.setSummary("", R.color.text_sub)
                if (!isCompact)
                    binding.setContent(listOf(createMessage(translate {
                        Locale.ENGLISH { "Show values with HomeWidget.set(key, label, value)" }
                        Locale.KOREAN  { "HomeWidget.set(key, label, value)로 값을 표시해 보세요" }
                    })))
                return
            }
            if (isCompact) {
                binding.setSummary("${latest.label} ${latest.value}", R.color.main_dark)
                return
            }
            val showProject = entries.distinctBy(ScriptWidgetEntries.Entry::projectId).size > 1
            binding.setSummary(if (showProject) "" else latest.projectName, R.color.text_sub)
            binding.setContent(entries.take(rowCapacity()).map { entry ->
                createRow(
                    title = if (showProject) "${entry.projectName} · ${entry.label}" else entry.label,
                    value = entry.value,
                    valueColorRes = R.color.main_dark,
                    fill = false,
                )
            })
        }

        render()
        repeatWhile(Lifecycle.State.STARTED) {
            render()
            ScriptWidgetEntries.updates.collect {
                render()
                delay(RENDER_INTERVAL)
            }
        }
    }
}

private val RENDER_INTERVAL = 300.milliseconds
