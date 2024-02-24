/*
 * DialogUtils.kt created by Minki Moon(mooner1022) on 2/17/24, 12:55 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.utils

import android.content.Context
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import dev.mooner.configdsl.R

typealias ConfirmCallback = (confirm: Boolean) -> Unit

fun MaterialDialog.setCommonAttrs() {
    cornerRadius(res = R.dimen.card_radius)
}

fun showConfirmDialog(context: Context, title: String, message: String, onDismiss: ConfirmCallback? = null) {
    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).noAutoDismiss().show {
        setCommonAttrs()
        cancelOnTouchOutside(false)
        title(text = title)
        message(text = message)
        positiveButton(res = R.string.ok) {
            onDismiss?.invoke(true)
            dismiss()
        }
        negativeButton(res = R.string.cancel) {
            onDismiss?.invoke(false)
            dismiss()
        }
    }
}