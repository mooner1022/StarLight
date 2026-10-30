package dev.mooner.configdsl.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import dev.mooner.configdsl.R

internal fun Context.copyToClipboard(label: CharSequence, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        Toast.makeText(this, R.string.configdsl_copied, Toast.LENGTH_SHORT).show()
}
