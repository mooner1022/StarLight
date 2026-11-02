package dev.mooner.starlight.ui.agent

import android.content.Context
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.model.TurnUsage
import dev.mooner.starlight.agent.provider.ProviderKind
import dev.mooner.starlight.agent.runtime.LiveRun
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import java.util.Locale

/** Small helpers the agent's screens share. */
internal object AgentUi {

    /** Markdown for model replies, in the agent's colors with monospace code; screens keep one each. */
    fun markdown(context: Context): Markwon =
        build(context)

    private fun build(context: Context): Markwon {
        val mono = ResourcesCompat.getFont(context, R.font.jbmono_nl_regular)
        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTypeface(mono ?: android.graphics.Typeface.MONOSPACE)
                        .codeBlockTypeface(mono ?: android.graphics.Typeface.MONOSPACE)
                        .codeBackgroundColor(ContextCompat.getColor(context, R.color.agent_code_bg))
                        .codeBlockBackgroundColor(ContextCompat.getColor(context, R.color.agent_code_bg))
                        .codeTextColor(ContextCompat.getColor(context, R.color.agent_text))
                        .codeBlockTextColor(ContextCompat.getColor(context, R.color.agent_text))
                        .codeTextSize(dp(context, 13))
                        .codeBlockMargin(dp(context, 10))
                        .linkColor(ContextCompat.getColor(context, R.color.agent_accent))
                        .blockQuoteColor(ContextCompat.getColor(context, R.color.agent_gold))
                        .bulletWidth(dp(context, 5))
                        .headingBreakHeight(0)
                        .thematicBreakColor(ContextCompat.getColor(context, R.color.agent_border))
                }
            })
            .build()
    }

    /** 1234 → "1.2k". */
    fun tokens(count: Long): String = when {
        count >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0)
        count >= 1_000     -> String.format(Locale.ROOT, "%.1fk", count / 1_000.0)
        else               -> count.toString()
    }

    fun usageLine(context: Context, usage: TurnUsage): String =
        context.getString(R.string.agent_usage, tokens(usage.input + usage.cacheWrite), tokens(usage.cacheRead), tokens(usage.output))

    fun cacheHitRate(usage: TurnUsage): String =
        if (usage.totalInput == 0L) "–" else "${usage.cacheRead * 100 / usage.totalInput}%"

    fun relativeTime(context: Context, millis: Long): String {
        val elapsed = System.currentTimeMillis() - millis
        if (elapsed < DateUtils.MINUTE_IN_MILLIS)
            return context.getString(R.string.agent_just_now)
        return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }

    /** "Claude Opus 5.5 · Anthropic" for the provider in use, or null if none is set up. */
    fun modelSummary(context: Context, hub: AgentHub): String? {
        val kind = hub.settings.activeProvider() ?: return null
        val modelId = hub.settings.model(kind) ?: return kind.displayName
        return context.getString(R.string.agent_subtitle_ready, modelName(hub, kind, modelId), kind.displayName)
    }

    fun modelName(hub: AgentHub, kind: ProviderKind, modelId: String): String =
        hub.settings.cachedModels(kind).firstOrNull { it.id == modelId }?.name ?: modelId

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

/**
 * The AI tab's subtitle in the main screen's header: what the agent is up to. The model isn't named here, since
 * the model chip right below already shows it.
 */
internal fun Context.agentSubtitle(): String {
    val hub = AgentHub.get(this)
    if (hub.settings.activeProvider() == null)
        return getString(R.string.agent_subtitle_setup)
    val live = hub.runtime.live.value
    val waiting = live.values.count { it.phase == LiveRun.Phase.APPROVAL }
    val conversations = hub.store.summaries.value.size
    return when {
        waiting > 0        -> getString(R.string.agent_subtitle_approval, live.size, waiting)
        live.isNotEmpty()  -> resources.getQuantityString(R.plurals.agent_subtitle_running, live.size, live.size)
        conversations == 0 -> getString(R.string.agent_subtitle_ready_empty)
        else               -> resources.getQuantityString(R.plurals.agent_subtitle_conversations, conversations, conversations)
    }
}
