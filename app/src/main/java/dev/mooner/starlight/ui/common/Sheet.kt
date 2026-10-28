package dev.mooner.starlight.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.DialogSheetBinding
import dev.mooner.starlight.databinding.ItemSheetCheckboxBinding
import dev.mooner.starlight.databinding.ItemSheetGridBinding

class SheetGridItem(
    @DrawableRes val icon: Int,
    val title: String,
)

class SheetBuilder internal constructor(private val context: Context) {

    var title: CharSequence = ""
    var message: CharSequence? = null
    var isMessageSelectable: Boolean = false
    var note: CharSequence? = null
    @DrawableRes
    var icon: Int = R.drawable.ic_store_sparkle
    var iconLoader: (ImageView.() -> Unit)? = null
    var cancelable: Boolean = true
    var cancelOnTouchOutside: Boolean = true
    var lifecycleOwner: LifecycleOwner? = null

    internal var positive: SheetAction? = null
    internal var negative: SheetAction? = null
    internal var neutral: SheetAction? = null
    internal val checkboxes = mutableListOf<Pair<CharSequence, Boolean>>()
    internal var content: ((LayoutInflater, LinearLayout) -> Unit)? = null
    internal var isContentScrollable = true
    internal var grid: SheetGrid? = null
    internal val onShow = mutableListOf<(Sheet) -> Unit>()
    internal val onDismiss = mutableListOf<() -> Unit>()

    fun positive(text: CharSequence, autoDismiss: Boolean = true, action: (Sheet) -> Unit = {}) {
        positive = SheetAction(text, autoDismiss, action)
    }

    fun positive(@StringRes res: Int, autoDismiss: Boolean = true, action: (Sheet) -> Unit = {}) =
        positive(context.getString(res), autoDismiss, action)

    fun negative(text: CharSequence, autoDismiss: Boolean = true, action: (Sheet) -> Unit = {}) {
        negative = SheetAction(text, autoDismiss, action)
    }

    fun negative(@StringRes res: Int, autoDismiss: Boolean = true, action: (Sheet) -> Unit = {}) =
        negative(context.getString(res), autoDismiss, action)

    fun neutral(text: CharSequence, autoDismiss: Boolean = true, action: (Sheet) -> Unit = {}) {
        neutral = SheetAction(text, autoDismiss, action)
    }

    fun checkbox(text: CharSequence, checked: Boolean = false) {
        checkboxes += text to checked
    }

    fun checkbox(@StringRes res: Int, checked: Boolean = false) =
        checkbox(context.getString(res), checked)

    fun content(scrollable: Boolean = true, block: (inflater: LayoutInflater, parent: LinearLayout) -> Unit) {
        content = block
        isContentScrollable = scrollable
    }

    fun content(view: View, scrollable: Boolean = true) =
        content(scrollable) { _, parent -> parent.addView(view) }

    fun grid(items: List<SheetGridItem>, selected: Int = -1, columns: Int = 3, onSelect: (sheet: Sheet, index: Int) -> Unit) {
        grid = SheetGrid(items, selected, columns, onSelect)
    }

    fun onShow(block: (Sheet) -> Unit) {
        onShow += block
    }

    fun onDismiss(block: () -> Unit) {
        onDismiss += block
    }
}

internal class SheetAction(
    val text: CharSequence,
    val autoDismiss: Boolean,
    val action: (Sheet) -> Unit,
)

internal class SheetGrid(
    val items: List<SheetGridItem>,
    val selected: Int,
    val columns: Int,
    val onSelect: (Sheet, Int) -> Unit,
)

class Sheet internal constructor(
    val dialog: SlideBottomSheetDialog,
    private val binding: DialogSheetBinding,
) {

    fun dismiss() =
        dialog.dismiss()

    fun isChecked(index: Int = 0): Boolean =
        (binding.checkboxes.getChildAt(index) as? CompoundButton)?.isChecked == true

    fun setTitle(text: CharSequence) {
        binding.title.text = text
    }

    fun setPositiveText(text: CharSequence) {
        binding.positive.text = text
    }

    fun setPositiveEnabled(enabled: Boolean) {
        binding.positive.isEnabled = enabled
        binding.positive.alpha = if (enabled) 1f else 0.4f
    }

    fun setNegativeText(text: CharSequence) {
        binding.negative.text = text
    }
}

fun Context.showSheet(block: SheetBuilder.() -> Unit): Sheet {
    val builder = SheetBuilder(this).apply(block)
    val dialog = SlideBottomSheetDialog(this)
    val inflater = LayoutInflater.from(this)
    val binding = DialogSheetBinding.inflate(inflater)
    val sheet = Sheet(dialog, binding)

    with(binding) {
        title.text = builder.title
        val loader = builder.iconLoader
        if (loader != null) {
            icon.imageTintList = null
            icon.layoutParams = icon.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            iconFrame.clipToOutline = true
            icon.loader()
        } else
            icon.setImageResource(builder.icon)
        message.setTextOrHide(builder.message)
        if (builder.isMessageSelectable)
            message.setTextIsSelectable(true)
        note.setTextOrHide(builder.note)

        val container = if (builder.isContentScrollable) content else fixedContent
        builder.content?.invoke(inflater, container)
        builder.grid?.let { container.addGrid(inflater, it, sheet) }
        content.isVisible = content.childCount > 0
        fixedContent.isVisible = fixedContent.childCount > 0

        for ((text, checked) in builder.checkboxes)
            ItemSheetCheckboxBinding.inflate(inflater, checkboxes, true).root.apply {
                this.text = text
                isChecked = checked
            }
        checkboxes.isVisible = builder.checkboxes.isNotEmpty()

        positive.bindAction(sheet, builder.positive)
        negative.bindAction(sheet, builder.negative)
        neutral.bindAction(sheet, builder.neutral)
        if (builder.positive == null && builder.negative == null && builder.neutral == null) {
            buttons.visibility = View.GONE
            buttonsDivider.visibility = View.GONE
            scroll.updatePadding(bottom = (20 * root.resources.displayMetrics.density).toInt())
        }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> updateDivider() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateDivider() }
    }

    dialog.setCancelable(builder.cancelable)
    dialog.setCanceledOnTouchOutside(builder.cancelable && builder.cancelOnTouchOutside)
    dialog.setContentView(binding.root)

    val owner = builder.lifecycleOwner ?: findLifecycleOwner()
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY)
            dialog.dismissImmediately()
    }
    owner?.lifecycle?.addObserver(observer)

    dialog.setOnShowListener {
        binding.playEnterAnimation()
        builder.onShow.forEach { it(sheet) }
    }
    dialog.setOnDismissListener {
        owner?.lifecycle?.removeObserver(observer)
        builder.onDismiss.forEach { it() }
    }
    dialog.show()
    return sheet
}

private fun Context.findLifecycleOwner(): LifecycleOwner? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<LifecycleOwner>()
        .firstOrNull()

private fun LinearLayout.addGrid(inflater: LayoutInflater, grid: SheetGrid, sheet: Sheet) {
    grid.items.chunked(grid.columns).forEachIndexed { rowIndex, row ->
        val rowView = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addView(rowView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.forEachIndexed { columnIndex, item ->
            val index = rowIndex * grid.columns + columnIndex
            ItemSheetGridBinding.inflate(inflater, rowView, true).apply {
                icon.setImageResource(item.icon)
                title.text = item.title
                root.isSelected = index == grid.selected
                root.setOnClickListener {
                    grid.onSelect(sheet, index)
                    sheet.dismiss()
                }
            }
        }
        repeat(grid.columns - row.size) {
            ItemSheetGridBinding.inflate(inflater, rowView, true).root.visibility = View.INVISIBLE
        }
    }
}

private fun TextView.setTextOrHide(value: CharSequence?) {
    text = value
    visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
}

private fun TextView.bindAction(sheet: Sheet, action: SheetAction?) {
    if (action == null) {
        visibility = View.GONE
        return
    }
    text = action.text
    setOnClickListener {
        if (action.autoDismiss)
            sheet.dismiss()
        action.action(sheet)
    }
}

private fun DialogSheetBinding.updateDivider() {
    val target = if (scroll.canScrollVertically(1)) 1f else 0f
    if (buttonsDivider.alpha != target)
        buttonsDivider.animate().alpha(target).setDuration(150).start()
}

private fun DialogSheetBinding.playEnterAnimation() {
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
    val targets = listOf(message) + content.children.toList() + listOf(note, checkboxes) + fixedContent.children.toList() + listOf(buttons)
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
