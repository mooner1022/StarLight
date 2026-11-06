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
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.WidgetPanelBinding
import dev.mooner.starlight.plugincore.widget.WidgetScope
import dev.mooner.starlight.utils.getColorCompat
import kotlin.math.roundToInt

internal val WidgetScope.isCompact: Boolean
    get() = span.y < 2

internal fun WidgetScope.panel(@DrawableRes icon: Int, @StringRes title: Int): WidgetPanelBinding =
    inflate(WidgetPanelBinding::inflate).apply {
        panelIcon.setImageResource(icon)
        panelTitle.setText(title)
        panelContent.isVisible = !isCompact
        root.gravity = if (isCompact) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
    }

internal fun WidgetPanelBinding.setSummary(text: String, @ColorRes colorRes: Int) {
    panelSummary.text = text
    panelSummary.setTextColor(root.context.getColorCompat(colorRes))
}

internal fun WidgetPanelBinding.setContent(views: List<View>) {
    panelContent.removeAllViews()
    views.forEach(panelContent::addView)
}

internal fun WidgetScope.createIcon(@DrawableRes icon: Int, @ColorRes tintRes: Int, size: Int): ImageView =
    ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(size), context.dp(size))
        setImageResource(icon)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(context.getColorCompat(tintRes)))
    }

internal fun WidgetScope.createText(text: String, sizeSp: Float, @ColorRes colorRes: Int, bold: Boolean = true): TextView =
    TextView(context).apply {
        this.text = text
        textSize = sizeSp
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        typeface = ResourcesCompat.getFont(context, if (bold) R.font.nanumsquare_round_bold else R.font.nanumsquare_round_regular)
        setTextColor(context.getColorCompat(colorRes))
    }

internal fun WidgetScope.createRow(
    title: String,
    value: String,
    @ColorRes valueColorRes: Int,
    @DrawableRes icon: Int? = null,
    @ColorRes iconTintRes: Int = valueColorRes,
    fill: Boolean = true,
): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = if (fill)
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        else
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(ROW_HEIGHT))
        if (icon != null)
            addView(createIcon(icon, iconTintRes, size = 18))
        addView(createText(title, sizeSp = 13f, colorRes = R.color.text).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (icon != null)
                    marginStart = context.dp(8)
            }
        })
        addView(createText(value, sizeSp = 13f, colorRes = valueColorRes).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(8)
            }
        })
    }

internal fun WidgetScope.createMessage(text: String): TextView =
    createText(text, sizeSp = 13f, colorRes = R.color.text_sub, bold = false).apply {
        gravity = Gravity.CENTER
        maxLines = 2
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
    }

internal fun View.setClickAction(action: ((View) -> Unit)?) {
    if (action == null) {
        setOnClickListener(null)
        isClickable = false
        background = null
        return
    }
    val outValue = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
    setBackgroundResource(outValue.resourceId)
    setOnClickListener(action)
}

internal fun WidgetScope.rowCapacity(rowHeight: Int = ROW_HEIGHT): Int {
    val resources = context.resources
    val cellHeight = resources.getDimensionPixelSize(R.dimen.widget_cell_height)
    val gap = resources.getDimensionPixelSize(R.dimen.widget_spacing)
    val contentHeight = span.y * cellHeight + (span.y - 1) * gap - context.dp(PANEL_CHROME)
    return (contentHeight / context.dp(rowHeight)).coerceAtLeast(1)
}

internal fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

private const val ROW_HEIGHT = 26
private const val PANEL_CHROME = 58
