package dev.mooner.starlight.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.WidgetStatusBinding
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
import dev.mooner.starlight.utils.getColorCompat
import kotlin.math.roundToInt

const val WIDGET_STATUS = "widget_status"

private typealias DiagnosticEntry = Pair<Diagnostic, DiagnosticResult>

val StatusWidget = widget(WIDGET_STATUS) {
    name {
        Locale.ENGLISH { "Bot status" }
        Locale.KOREAN  { "봇 상태" }
    }
    span(default = 4 x 2, min = 2 x 1, max = 8 x 4)

    content {
        val binding = inflate(WidgetStatusBinding::inflate)
        val isCompact = span.y < 2
        val isDetailed = span.y >= 3
        binding.statusItems.isVisible = !isCompact
        binding.statusItems.orientation = if (isDetailed) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        binding.root.gravity = if (isCompact) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY

        fun render() {
            val entries = Diagnostics.checkAll(context)
            val problems = entries.filter { (_, result) -> result.level.isProblem }
            binding.textStatusSummary.apply {
                text = if (problems.isEmpty())
                    translate {
                        Locale.ENGLISH { "All good" }
                        Locale.KOREAN  { "모두 정상" }
                    }
                else
                    translate {
                        Locale.ENGLISH { "${problems.size} to check" }
                        Locale.KOREAN  { "${problems.size}개 확인 필요" }
                    }
                setTextColor(context.getColorCompat(if (problems.isEmpty()) R.color.status_ok else R.color.status_error))
            }
            if (isCompact) {
                val fixable = problems.firstOrNull { (diagnostic, _) -> diagnostic.isFixable }
                binding.root.setOnClickListener(fixable?.let { (diagnostic, _) -> View.OnClickListener { diagnostic.fix(it.context) } })
                binding.root.isClickable = fixable != null
                return
            }
            binding.statusItems.removeAllViews()
            for (entry in entries) {
                val item = if (isDetailed)
                    createDetailedItem(entry)
                else
                    createIconItem(entry, showLabel = span.x > 2)
                binding.statusItems.addView(item)
            }
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
        addView(createIcon(diagnostic, result.level, size = 22))
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
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(createIcon(diagnostic, result.level, size = 18))
        addView(createText(diagnostic.title, sizeSp = 13f, colorRes = R.color.text).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = context.dp(8)
            }
        })
        addView(createText(result.summary, sizeSp = 13f, colorRes = result.level.colorRes))
        bindFix(this, diagnostic, result)
    }
}

private fun WidgetScope.createIcon(diagnostic: Diagnostic, level: DiagnosticLevel, size: Int): ImageView =
    ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(size), context.dp(size))
        setImageResource(diagnostic.icon)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(context.getColorCompat(level.colorRes)))
    }

private fun WidgetScope.createText(text: String, sizeSp: Float, colorRes: Int): TextView =
    TextView(context).apply {
        this.text = text
        textSize = sizeSp
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        typeface = ResourcesCompat.getFont(context, R.font.nanumsquare_round_bold)
        setTextColor(context.getColorCompat(colorRes))
    }

private fun bindFix(view: View, diagnostic: Diagnostic, result: DiagnosticResult) {
    if (!result.level.isProblem || !diagnostic.isFixable)
        return
    val outValue = TypedValue()
    view.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
    view.setBackgroundResource(outValue.resourceId)
    view.setOnClickListener { diagnostic.fix(it.context) }
}

private val DiagnosticLevel.colorRes: Int
    get() = when (this) {
        DiagnosticLevel.OK      -> R.color.status_ok
        DiagnosticLevel.INFO    -> R.color.text_sub
        DiagnosticLevel.WARNING -> R.color.status_warning
        DiagnosticLevel.ERROR   -> R.color.status_error
    }

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()
