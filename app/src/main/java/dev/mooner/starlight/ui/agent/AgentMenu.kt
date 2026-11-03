package dev.mooner.starlight.ui.agent

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import dev.mooner.starlight.R
import me.saket.cascade.CascadePopupMenu
import kotlin.math.ceil

/**
 * The agent screens' drop-down menu: a rounded card that stands out from the page, white with a shadow in the light
 * theme and the night header's charcoal in the dark.
 */
internal object AgentMenu {

    data class Item(val id: Int, @StringRes val title: Int, @DrawableRes val icon: Int, val danger: Boolean = false)

    fun show(anchor: View, items: List<Item>, onClick: (id: Int) -> Unit) {
        val context = anchor.context
        fun color(res: Int) = ContextCompat.getColor(context, res)
        val font = ResourcesCompat.getFont(context, R.font.wantedsans_medium)
        val dangerous = items.filter { it.danger }.mapTo(HashSet()) { it.id }

        val styler = CascadePopupMenu.Styler(
            background = {
                GradientDrawable().apply {
                    cornerRadius = AgentUi.dp(context, 16).toFloat()
                    setColor(color(R.color.agent_popup))
                    setStroke(AgentUi.dp(context, 1), color(R.color.agent_popup_border))
                }
            },
            menuList = { list ->
                val padding = AgentUi.dp(context, 6)
                list.setPadding(0, padding, 0, padding)
                list.clipToPadding = false
            },
            menuItem = { holder ->
                val danger = holder.model.item.itemId in dangerous
                val tint = if (danger) color(R.color.agent_popup_danger) else color(R.color.agent_popup_text)
                holder.titleView.typeface = font
                holder.titleView.textSize = TITLE_SIZE_SP
                // The theme spaces popup titles out a little; without it they are as wide as [fittedWidth] measures.
                holder.titleView.letterSpacing = 0f
                holder.titleView.setTextColor(tint)
                holder.iconView.imageTintList =
                    ColorStateList.valueOf(if (danger) tint else color(R.color.agent_popup_icon))
                // The theme's ripple would vanish on charcoal; each theme has its own.
                holder.setBackground(RippleDrawable(ColorStateList.valueOf(color(R.color.agent_popup_ripple)), null,
                    Color.WHITE.toDrawable()))
            },
        )
        val menu = CascadePopupMenu(context, anchor, Gravity.END, styler, fittedWidth(anchor, items, font))
        items.forEachIndexed { order, item -> menu.menu.add(0, item.id, order, item.title).setIcon(item.icon) }
        menu.popup.elevation = AgentUi.dp(context, 12).toFloat()
        menu.setOnMenuItemClickListener { item ->
            onClick(item.itemId)
            true
        }
        menu.show()
        // The anchor sits at the screen's edge and cascade sets its own small margins while showing, so move the
        // card in a little afterwards.
        menu.popup.update(anchor, -AgentUi.dp(context, 8), 0, -1, -1)
    }

    /** Just wide enough for the longest title, in whatever language the titles are in. */
    private fun fittedWidth(anchor: View, items: List<Item>, font: Typeface?): Int {
        val context = anchor.context
        val paint = TextPaint().apply {
            typeface = font
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_SIZE_SP, context.resources.displayMetrics)
        }
        val longest = items.maxOf { paint.measureText(context.getString(it.title)) }
        return ceil(longest).toInt() + AgentUi.dp(context, ITEM_CHROME_DP)
    }

    private const val TITLE_SIZE_SP = 14.5f
    /** An item's paddings and icon around its title. */
    private const val ITEM_CHROME_DP = 66
}
