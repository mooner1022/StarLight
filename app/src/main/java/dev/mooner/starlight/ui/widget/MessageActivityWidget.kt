package dev.mooner.starlight.ui.widget

import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.stats.MessageStats
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val WIDGET_MESSAGES = "widget_messages"

val MessageActivityWidget = widget(WIDGET_MESSAGES) {
    name {
        Locale.ENGLISH { "Message activity" }
        Locale.KOREAN  { "메시지 활동량" }
    }
    span(default = 4 x 2, min = 2 x 1, max = 8 x 6)

    content {
        val binding = panel(R.drawable.ic_round_insert_comment_24, R.string.widget_messages)
        val roomRows = if (span.y >= 3) (rowCapacity() - CHART_ROWS).coerceAtLeast(0) else 0
        val chart = ActivityChartView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        fun render() {
            val counts = MessageStats.recentCounts()
            binding.setSummary(translate {
                Locale.ENGLISH { "${counts.sum()} in last hour" }
                Locale.KOREAN  { "최근 1시간 ${counts.sum()}개" }
            }, R.color.text_sub)
            if (isCompact)
                return
            chart.values = counts
            val rooms = MessageStats.topRooms(roomRows).map { (room, count) ->
                createRow(title = room, value = count.toString(), valueColorRes = R.color.main_dark, fill = false)
            }
            binding.setContent(listOf(chart) + rooms)
        }

        render()
        ticker(30.seconds) { render() }
        repeatWhile(Lifecycle.State.STARTED) {
            render()
            MessageStats.updates.collect {
                render()
                delay(RENDER_INTERVAL)
            }
        }
    }
}

private const val CHART_ROWS = 3
private val RENDER_INTERVAL = 500.milliseconds
