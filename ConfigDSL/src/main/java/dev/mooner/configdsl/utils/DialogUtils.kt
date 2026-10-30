/*
 * DialogUtils.kt created by Minki Moon(mooner1022) on 2/17/24, 12:55 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import dev.mooner.configdsl.ConfigDSL
import dev.mooner.configdsl.R

typealias ConfirmCallback = (confirm: Boolean) -> Unit

enum class PeekType { INFO, SUCCESS, ERROR }

interface ConfigDialogProvider {

    fun confirm(context: Context, title: String, message: String, onResult: ConfirmCallback)

    fun form(context: Context, title: String, content: View, onConfirm: () -> Unit, onDismiss: () -> Unit)

    fun password(context: Context, title: String, validate: (String) -> String?, onConfirm: (String) -> Unit)

    fun input(context: Context, title: String, hint: String, initial: String, onResult: (String?) -> Unit) =
        MaterialDialogProvider.input(context, title, hint, initial, onResult)

    fun select(context: Context, title: String, items: List<String>, selected: Int, onResult: (Int?) -> Unit) =
        MaterialDialogProvider.select(context, title, items, selected, onResult)

    fun multiSelect(context: Context, title: String, items: List<String>, selected: Set<Int>, onResult: (Set<Int>?) -> Unit) =
        MaterialDialogProvider.multiSelect(context, title, items, selected, onResult)

    fun peek(view: View, message: String, type: PeekType) =
        Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
}

internal object MaterialDialogProvider : ConfigDialogProvider {

    override fun confirm(context: Context, title: String, message: String, onResult: ConfirmCallback) {
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).noAutoDismiss().show {
            setCommonAttrs()
            cancelOnTouchOutside(false)
            title(text = title)
            message(text = message)
            positiveButton(res = R.string.ok) {
                onResult(true)
                dismiss()
            }
            negativeButton(res = R.string.cancel) {
                onResult(false)
                dismiss()
            }
        }
    }

    override fun form(context: Context, title: String, content: View, onConfirm: () -> Unit, onDismiss: () -> Unit) {
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            title(text = title)
            setCommonAttrs()
            if (context is LifecycleOwner)
                lifecycleOwner(context)
            customView(view = content)
            onDismiss { onDismiss() }
            positiveButton(R.string.ok) { onConfirm() }
            negativeButton(R.string.cancel)
        }
    }

    override fun password(context: Context, title: String, validate: (String) -> String?, onConfirm: (String) -> Unit) {
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT))
            .input(waitForPositiveButton = false) { dialog, text ->
                dialog.getInputField().error = validate(text.toString())
            }.show {
                setCommonAttrs()
                title(text = title)
                positiveButton(R.string.ok) { dialog ->
                    onConfirm(dialog.getInputField().text.toString())
                }
                negativeButton(R.string.cancel, click = MaterialDialog::dismiss)
            }
    }

    override fun input(context: Context, title: String, hint: String, initial: String, onResult: (String?) -> Unit) {
        var result: String? = null
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT))
            .input(hint = hint, prefill = initial) { _, text -> result = text.toString() }
            .show {
                setCommonAttrs()
                title(text = title)
                positiveButton(R.string.ok)
                negativeButton(R.string.cancel)
                onDismiss { onResult(result) }
            }
    }

    override fun select(context: Context, title: String, items: List<String>, selected: Int, onResult: (Int?) -> Unit) {
        var result: Int? = null
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrs()
            title(text = title)
            listItemsSingleChoice(items = items, initialSelection = selected) { _, index, _ -> result = index }
            positiveButton(R.string.ok)
            negativeButton(R.string.cancel)
            onDismiss { onResult(result) }
        }
    }

    override fun multiSelect(context: Context, title: String, items: List<String>, selected: Set<Int>, onResult: (Set<Int>?) -> Unit) {
        var result: Set<Int>? = null
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrs()
            title(text = title)
            listItemsMultiChoice(items = items, initialSelection = selected.toIntArray(), allowEmptySelection = true) { _, indices, _ ->
                result = indices.toSet()
            }
            positiveButton(R.string.ok)
            negativeButton(R.string.cancel)
            onDismiss { onResult(result) }
        }
    }
}

fun MaterialDialog.setCommonAttrs() {
    cornerRadius(res = R.dimen.card_radius)
}

fun showConfirmDialog(context: Context, title: String, message: String, onDismiss: ConfirmCallback? = null) =
    ConfigDSL.dialogProvider.confirm(context, title, message) { onDismiss?.invoke(it) }
