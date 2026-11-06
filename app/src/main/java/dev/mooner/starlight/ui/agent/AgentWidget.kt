package dev.mooner.starlight.ui.agent

import androidx.lifecycle.Lifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.runtime.AgentNotifier
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.ui.widget.createRow
import dev.mooner.starlight.ui.widget.isCompact
import dev.mooner.starlight.ui.widget.panel
import dev.mooner.starlight.ui.widget.rowCapacity
import dev.mooner.starlight.ui.widget.setClickAction
import dev.mooner.starlight.ui.widget.setContent
import dev.mooner.starlight.ui.widget.setSummary
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val WIDGET_AGENT = "widget_agent"

/** The AI agent on the home screen: what it is doing, and a way to ask it something. */
val AgentWidget = widget(WIDGET_AGENT) {
    name {
        Locale.ENGLISH { "AI agent" }
        Locale.KOREAN  { "AI 에이전트" }
    }
    span(default = 4 x 2, min = 2 x 1, max = 8 x 4)

    content {
        val binding = panel(R.drawable.ic_agent_sparkle, R.string.agent_widget)
        val hub = AgentHub.get(context)

        repeatWhile(Lifecycle.State.STARTED) {
            val phases = hub.runtime.live
                .map { runs -> runs.mapValues { (_, run) -> run.copy(text = "", thinking = "") } }
                .distinctUntilChanged()
            combine(hub.store.summaries, phases) { summaries, live -> summaries to live }.collect { (summaries, live) ->
                val running = summaries.filter { it.id in live }
                if (running.isNotEmpty())
                    binding.setSummary(context.getString(R.string.agent_widget_running, running.size), R.color.agent_meteor)
                else
                    binding.setSummary(context.getString(R.string.agent_widget_idle), R.color.main_bright)
                val target = running.firstOrNull()?.id
                binding.root.setClickAction { view -> view.context.startActivity(AgentChatActivity.intent(view.context, target)) }
                if (isCompact)
                    return@collect
                val shown = (running + summaries.filter { it.id !in live }).take(rowCapacity())
                binding.setContent(shown.map { item ->
                    val run = live[item.id]
                    createRow(
                        title = item.title,
                        value = run?.let { AgentNotifier.phaseText(context, it) } ?: AgentUi.relativeTime(context, item.updatedAt),
                        valueColorRes = if (run != null) R.color.agent_meteor else R.color.text_sub,
                        fill = false,
                    ).apply {
                        setClickAction { view -> view.context.startActivity(AgentChatActivity.intent(view.context, item.id)) }
                    }
                })
            }
        }
    }
}
