package dev.mooner.starlight.ui.common

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import dev.mooner.configdsl.utils.ConfigDialogProvider
import dev.mooner.configdsl.utils.ConfirmCallback
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ItemSheetOptionBinding
import dev.mooner.starlight.databinding.ItemSheetPasswordBinding
import dev.mooner.starlight.utils.peek
import dev.mooner.starlight.utils.showConfirmDialog

object SheetDialogProvider : ConfigDialogProvider {

    override fun confirm(context: Context, title: String, message: String, onResult: ConfirmCallback) =
        showConfirmDialog(context, title, message, onResult)

    override fun form(context: Context, title: String, content: View, onConfirm: () -> Unit, onDismiss: () -> Unit) {
        context.showSheet {
            this.title = title
            icon = R.drawable.ic_round_edit_24
            this.content(content)
            positive(R.string.ok) { onConfirm() }
            negative(R.string.cancel)
            this.onDismiss(onDismiss)
        }
    }

    override fun password(context: Context, title: String, validate: (String) -> String?, onConfirm: (String) -> Unit) {
        val binding = ItemSheetPasswordBinding.inflate(LayoutInflater.from(context))
        binding.input.doAfterTextChanged { binding.root.error = validate(it.toString()) }
        context.showSheet {
            this.title = title
            icon = dev.mooner.configdsl.R.drawable.ic_round_lock_24
            content(binding.root)
            negative(R.string.cancel)
            positive(R.string.ok, autoDismiss = false) { sheet ->
                val password = binding.input.text.toString()
                val error = validate(password)
                if (error != null) {
                    binding.root.error = error
                    return@positive
                }
                sheet.dismiss()
                onConfirm(password)
            }
        }
    }

    override fun peek(view: View, message: String, type: PeekType) =
        view.context.peek(message, type)

    override fun input(context: Context, title: String, hint: String, initial: String, onResult: (String?) -> Unit) {
        val binding = ItemSheetPasswordBinding.inflate(LayoutInflater.from(context))
        binding.root.endIconMode = TextInputLayout.END_ICON_NONE
        binding.root.isErrorEnabled = false
        binding.input.inputType = InputType.TYPE_CLASS_TEXT
        binding.input.hint = hint
        binding.input.setText(initial)
        var result: String? = null
        context.showSheet {
            this.title = title
            icon = R.drawable.ic_round_edit_24
            content(binding.root)
            negative(R.string.cancel)
            positive(R.string.ok) { result = binding.input.text.toString() }
            onDismiss { onResult(result) }
        }
    }

    override fun select(context: Context, title: String, items: List<String>, selected: Int, onResult: (Int?) -> Unit) {
        var current = selected.takeIf { it in items.indices }
        var result: Int? = null
        lateinit var sheet: Sheet
        val cards = OptionCards(context, items, isSelected = { it == current }) { index ->
            current = index
            sheet.setPositiveEnabled(true)
        }
        sheet = context.showSheet {
            this.title = title
            icon = dev.mooner.configdsl.R.drawable.ic_round_format_list_bulleted_24
            content(cards.view)
            negative(R.string.cancel)
            positive(R.string.ok) { result = current }
            onDismiss { onResult(result) }
        }
        sheet.setPositiveEnabled(current != null)
    }

    override fun multiSelect(context: Context, title: String, items: List<String>, selected: Set<Int>, onResult: (Set<Int>?) -> Unit) {
        val checked = selected.toMutableSet()
        var result: Set<Int>? = null
        val cards = OptionCards(context, items, isSelected = { it in checked }) { index ->
            if (!checked.remove(index))
                checked += index
        }
        context.showSheet {
            this.title = title
            icon = dev.mooner.configdsl.R.drawable.ic_round_format_list_bulleted_24
            content(cards.view)
            negative(R.string.cancel)
            positive(R.string.ok) { result = checked.sorted().toSet() }
            onDismiss { onResult(result) }
        }
    }
}

private class OptionCards(
    context: Context,
    items: List<String>,
    private val isSelected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
) {

    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val cards: List<MaterialCardView> = items.mapIndexed { index, item ->
        ItemSheetOptionBinding.inflate(LayoutInflater.from(context), view, true).run {
            label.text = item
            root.setOnClickListener {
                onClick(index)
                refresh()
            }
            root
        }
    }

    init {
        refresh()
    }

    private fun refresh() =
        cards.forEachIndexed { index, card -> card.setSelectionHighlight(isSelected(index)) }

    private fun MaterialCardView.setSelectionHighlight(selected: Boolean) {
        if (selected) {
            strokeWidth = (2 * resources.displayMetrics.density).toInt()
            strokeColor = context.getColor(R.color.main_bright)
            setCardBackgroundColor(Color.parseColor(SELECTED_OPTION_COLOR))
        } else {
            strokeWidth = 0
            setCardBackgroundColor(context.getColor(dev.mooner.configdsl.R.color.background_selection_card))
        }
    }
}

private const val SELECTED_OPTION_COLOR = "#50AD977A"
