package dev.mooner.starlight.ui.widget

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.diagnostics.Diagnostic
import dev.mooner.starlight.diagnostics.DiagnosticLevel
import dev.mooner.starlight.diagnostics.DiagnosticResult
import dev.mooner.starlight.diagnostics.Diagnostics
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.widget.WidgetScope
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x

const val WIDGET_STATUS = "widget_status"

private typealias DiagnosticEntry = Pair<Diagnostic, DiagnosticResult>

val StatusWidget = widget(WIDGET_STATUS) {
    name {
        Locale.ENGLISH { "Bot status" }
        Locale.KOREAN  { "봇 상태" }
    }
    span(default = 4 x 2, min = 2 x 1, max = 8 x 4)

    content {
        val binding = panel(R.drawable.ic_round_power_settings_new_24, R.string.widget_status)
        val isDetailed = span.y >= 3
        binding.panelContent.orientation = if (isDetailed) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        fun render() {
            val entries = Diagnostics.checkAll(context)
            val problems = entries.filter { (_, result) -> result.level.isProblem }
            if (problems.isEmpty())
                binding.setSummary(translate {
                    Locale.ENGLISH { "All good" }
                    Locale.KOREAN  { "모두 정상" }
                }, R.color.status_ok)
            else
                binding.setSummary(translate {
                    Locale.ENGLISH { "${problems.size} to check" }
                    Locale.KOREAN  { "${problems.size}개 확인 필요" }
                }, R.color.status_error)

            if (isCompact) {
                val fixable = problems.firstOrNull { (diagnostic, _) -> diagnostic.isFixable }
                binding.root.setClickAction(fixable?.let { (diagnostic, _) -> { view: View -> diagnostic.fix(view.context) } })
                return
            }
            binding.setContent(entries.map { entry ->
                if (isDetailed)
                    createDetailedItem(entry)
                else
                    createIconItem(entry, showLabel = span.x > 2)
            })
        }

        render()
        repeatWhile(Lifecycle.State.RESUMED) { render() }
        on<Events.Config.GlobalConfigUpdate> { render() }
        on<Events.Project.Create> { render() }
        on<Events.Project.InfoUpdate> { render() }
        on<Events.Project.Compile> { render() }
        on<Events.Project.Delete> { render() }
    }
}

private fun WidgetScope.createIconItem(entry: DiagnosticEntry, showLabel: Boolean): View {
    val (diagnostic, result) = entry
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        contentDescription = "${diagnostic.title}: ${result.summary}"
        addView(createIcon(diagnostic.icon, result.level.colorRes, size = 22))
        if (showLabel)
            addView(createText(diagnostic.title, sizeSp = 11f, colorRes = R.color.text).apply {
                gravity = Gravity.CENTER
                setPadding(0, context.dp(4), 0, 0)
            })
        bindFix(this, diagnostic, result)
    }
}

private fun WidgetScope.createDetailedItem(entry: DiagnosticEntry): View {
    val (diagnostic, result) = entry
    return createRow(
        title = diagnostic.title,
        value = result.summary,
        valueColorRes = result.level.colorRes,
        icon = diagnostic.icon,
    ).also { bindFix(it, diagnostic, result) }
}

private fun bindFix(view: View, diagnostic: Diagnostic, result: DiagnosticResult) {
    if (result.level.isProblem && diagnostic.isFixable)
        view.setClickAction { diagnostic.fix(it.context) }
}

private val DiagnosticLevel.colorRes: Int
    get() = when (this) {
        DiagnosticLevel.OK      -> R.color.status_ok
        DiagnosticLevel.INFO    -> R.color.text_sub
        DiagnosticLevel.WARNING -> R.color.status_warning
        DiagnosticLevel.ERROR   -> R.color.status_error
    }
