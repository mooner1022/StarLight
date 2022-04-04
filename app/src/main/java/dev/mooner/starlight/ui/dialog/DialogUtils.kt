package dev.mooner.starlight.ui.dialog

import android.annotation.SuppressLint
import android.content.Context
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import dev.mooner.starlight.R

typealias EmptyCallback = () -> Unit
typealias ConfirmCallback = (confirm: Boolean) -> Unit

class DialogUtils {
    companion object {
        @SuppressLint("CheckResult")
        @JvmStatic
        fun showConfirmDialog(context: Context, title: String, message: String, onDismiss: ConfirmCallback? = null) {
            MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(res = R.dimen.card_radius)
                cancelOnTouchOutside(false)
                noAutoDismiss()
                title(text = title)
                message(text = message)
                positiveButton(text = context.getString(R.string.ok)) {
                    onDismiss?.invoke(true)
                    dismiss()
                }
                negativeButton(text = context.getString(R.string.cancel)) {
                    onDismiss?.invoke(false)
                    dismiss()
                }
            }
        }
    }
}