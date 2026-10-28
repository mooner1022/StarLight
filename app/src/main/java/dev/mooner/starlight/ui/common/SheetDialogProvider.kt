package dev.mooner.starlight.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.doAfterTextChanged
import dev.mooner.configdsl.utils.ConfigDialogProvider
import dev.mooner.configdsl.utils.ConfirmCallback
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ItemSheetPasswordBinding
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
}
