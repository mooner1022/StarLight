package dev.mooner.starlight.ui.common

import androidx.core.graphics.toColorInt
import com.google.android.material.card.MaterialCardView
import dev.mooner.starlight.R

private const val SELECTED_OPTION_COLOR = "#50AD977A"

/** Shows whether a choice card is picked, the way the app's selection sheets do. */
internal fun MaterialCardView.setOptionSelected(selected: Boolean) {
    if (selected) {
        strokeWidth = (2 * resources.displayMetrics.density).toInt()
        strokeColor = context.getColor(R.color.main_bright)
        setCardBackgroundColor(SELECTED_OPTION_COLOR.toColorInt())
    } else {
        strokeWidth = 0
        setCardBackgroundColor(context.getColor(dev.mooner.configdsl.R.color.background_selection_card))
    }
}
