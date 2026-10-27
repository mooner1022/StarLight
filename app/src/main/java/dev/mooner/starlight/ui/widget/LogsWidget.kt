package dev.mooner.starlight.ui.widget

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import dev.mooner.starlight.CA_DEV_MODE
import dev.mooner.starlight.databinding.CardLogBinding
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

const val WIDGET_LOGS = "widget_logs"

private const val MAX_CANDIDATES = 10
private const val MAX_MESSAGE_LINES = 3
private val RENDER_DELAY = 200.milliseconds

val LogsWidget = widget(WIDGET_LOGS) {
    name {
        Locale.ENGLISH { "Logs" }
        Locale.KOREAN  { "로그" }
    }
    span(default = 4 x 4, min = 2 x 2, max = 8 x 8)

    content {
        val binding = inflate(WidgetLogsBinding::inflate)
        val inflater = LayoutInflater.from(context)
        val shown: ArrayDeque<LogData> = ArrayDeque()

        fun isVisibleLog(log: LogData): Boolean =
            log.type != LogType.VERBOSE || GlobalConfig.category(CA_DEV_MODE).getBoolean("show_internal_log", false)

        fun render() {
            val hasLogs = shown.isNotEmpty()
            binding.textViewNoLogsYet.isVisible = !hasLogs
            binding.logList.isVisible = hasLogs
            binding.logList.removeAllViews()
            for (log in shown) {
                val item = CardLogBinding.inflate(inflater, binding.logList, false)
                LogItem().withLogData(log, LogItem.ViewType.NORMAL, copyOnLongClick = false).bindView(item, emptyList())
                item.logContentText.maxLines = MAX_MESSAGE_LINES
                item.logContentText.ellipsize = TextUtils.TruncateAt.END
                binding.logList.addView(item.root)
            }
        }

        var pendingRender: Job? = null
        fun scheduleRender() {
            if (pendingRender?.isActive == true)
                return
            pendingRender = launch {
                delay(RENDER_DELAY)
                render()
            }
        }

        fun push(log: LogData) {
            shown.addFirst(log)
            while (shown.size > MAX_CANDIDATES)
                shown.removeLast()
        }

        val openLogs = View.OnClickListener { it.context.showLogsDialog() }
        binding.tvMoreLogs.setOnClickListener(openLogs)
        binding.buttonMoreLogs.setOnClickListener(openLogs)

        if (isPreview) {
            repeat(MAX_CANDIDATES) { push(previewLog()) }
            render()
            return@content
        }

        repeatWhile(Lifecycle.State.STARTED) {
            shown.clear()
            LogCollector.logs
                .filter(::isVisibleLog)
                .takeLast(MAX_CANDIDATES)
                .forEach(::push)
            render()
        }
        on<Events.Log.Create> {
            if (!isVisibleLog(log))
                return@on
            push(log)
            scheduleRender()
        }
    }
}

private fun previewLog(): LogData =
    LogData(
        type = LogType.INFO,
        message = translate {
            Locale.ENGLISH { "This is a dummy log for preview.\nMessage is shown here." }
            Locale.KOREAN  { "이건 미리보기용 더미 로그에요.\n로그 메세지는 여기에 보여져요." }
        }
    )
