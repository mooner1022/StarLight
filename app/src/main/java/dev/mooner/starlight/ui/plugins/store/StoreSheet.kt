package dev.mooner.starlight.ui.plugins.store

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.children
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.DialogStoreSheetBinding
import dev.mooner.starlight.ui.common.SlideBottomSheetDialog

internal class StoreSheetBuilder {

    var title: String = ""
    var message: CharSequence? = null
    var note: CharSequence? = null
    @DrawableRes
    var icon: Int = R.drawable.ic_store_sparkle

    internal var positive: Pair<String, () -> Unit>? = null
    internal var negative: Pair<String, () -> Unit>? = null
    internal var content: ((LayoutInflater, LinearLayout) -> Unit)? = null

    fun positive(text: String, action: () -> Unit = {}) {
        positive = text to action
    }

    fun negative(text: String, action: () -> Unit = {}) {
        negative = text to action
    }

    fun content(block: (inflater: LayoutInflater, parent: LinearLayout) -> Unit) {
        content = block
    }
}

internal fun Context.showStoreSheet(block: StoreSheetBuilder.() -> Unit): SlideBottomSheetDialog {
    val builder = StoreSheetBuilder().apply(block)
    val dialog = SlideBottomSheetDialog(this)
    val inflater = LayoutInflater.from(this)
    val binding = DialogStoreSheetBinding.inflate(inflater)

    with(binding) {
        title.text = builder.title
        icon.setImageResource(builder.icon)
        message.setTextOrHide(builder.message)
        note.setTextOrHide(builder.note)
        builder.content?.invoke(inflater, content)
        content.visibility = if (content.childCount > 0) View.VISIBLE else View.GONE
        positive.bindAction(dialog, builder.positive)
        negative.bindAction(dialog, builder.negative)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> updateDivider() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateDivider() }
    }

    dialog.setContentView(binding.root)
    dialog.setOnShowListener { binding.playEnterAnimation() }
    dialog.show()
    return dialog
}

private fun TextView.setTextOrHide(value: CharSequence?) {
    text = value
    visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
}

private fun TextView.bindAction(dialog: SlideBottomSheetDialog, action: Pair<String, () -> Unit>?) {
    if (action == null) {
        visibility = View.GONE
        return
    }
    text = action.first
    setOnClickListener {
        dialog.dismiss()
        action.second()
    }
}

private fun DialogStoreSheetBinding.updateDivider() {
    val target = if (scroll.canScrollVertically(1)) 1f else 0f
    if (buttonsDivider.alpha != target)
        buttonsDivider.animate().alpha(target).setDuration(150).start()
}

private fun DialogStoreSheetBinding.playEnterAnimation() {
    iconFrame.scaleX = 0.4f
    iconFrame.scaleY = 0.4f
    iconFrame.animate()
        .scaleX(1f).scaleY(1f)
        .setStartDelay(60)
        .setDuration(480)
        .setInterpolator(OvershootInterpolator(2.4f))
        .start()
    icon.rotation = -120f
    icon.animate()
        .rotation(0f)
        .setStartDelay(60)
        .setDuration(620)
        .setInterpolator(DecelerateInterpolator(2f))
        .start()

    val offset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, root.resources.displayMetrics)
    val targets = listOf(message) + content.children.toList() + listOf(note, buttons)
    targets.filter { it.visibility == View.VISIBLE }.forEachIndexed { i, view ->
        view.alpha = 0f
        view.translationY = offset
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(120L + i * 45L)
            .setDuration(360)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }
}
