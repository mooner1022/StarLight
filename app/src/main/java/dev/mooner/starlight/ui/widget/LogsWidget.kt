package dev.mooner.starlight.ui.widget

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import dev.mooner.starlight.CA_DEV_MODE
import dev.mooner.starlight.databinding.WidgetLogsBinding
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.ui.logs.LogItem
import dev.mooner.starlight.utils.showLogsDialog
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator

const val WIDGET_LOGS = "widget_logs"

val LogsWidget = widget(WIDGET_LOGS) {
    name {
        Locale.ENGLISH { "Logs" }
        Locale.KOREAN  { "로그" }
    }
    span(default = 4 x 4, min = 2 x 2, max = 8 x 8)

    content {
        val binding = inflate(WidgetLogsBinding::inflate)
        val maxLogs = (span.y - 1).coerceAtLeast(1)
        val itemAdapter = ItemAdapter<LogItem>()
        binding.rvLogs.apply {
            itemAnimator = FadeInUpAnimator()
            layoutManager = LinearLayoutManager(context).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            adapter = FastAdapter.with(itemAdapter)
        }

        fun setLogsVisible(visible: Boolean) {
            binding.textViewNoLogsYet.isVisible = !visible
            binding.rvLogs.isVisible = visible
        }

        fun showLogs(logs: List<LogData>) {
            itemAdapter.set(logs.takeLast(maxLogs).map(LogData::toLogItem))
            setLogsVisible(logs.isNotEmpty())
        }

        val openLogs = View.OnClickListener { it.context.showLogsDialog() }
        binding.tvMoreLogs.setOnClickListener(openLogs)
        binding.buttonMoreLogs.setOnClickListener(openLogs)

        if (isPreview) {
            showLogs(List(maxLogs) { previewLog() })
            return@content
        }

        repeatWhile(Lifecycle.State.STARTED) {
            showLogs(LogCollector.logs.filter(::isVisibleLog))
        }
        on<Events.Log.Create> {
            if (!isVisibleLog(log))
                return@on
            if (itemAdapter.adapterItemCount >= maxLogs)
                itemAdapter.remove(0)
            itemAdapter.add(log.toLogItem())
            setLogsVisible(true)
        }
    }
}

private fun isVisibleLog(log: LogData): Boolean =
    log.type != LogType.VERBOSE ||
            GlobalConfig.category(CA_DEV_MODE).getBoolean("show_internal_log", false)

private fun previewLog(): LogData =
    LogData(
        type = LogType.INFO,
        message = translate {
            Locale.ENGLISH { "This is a dummy log for preview.\nMessage is shown here." }
            Locale.KOREAN  { "이건 미리보기용 더미 로그에요.\n로그 메세지는 여기에 보여져요." }
        }
    )

private fun LogData.toLogItem(): LogItem =
    LogItem().withLogData(this, LogItem.ViewType.NORMAL)
