package com.mooner.starlight.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri

class FileUtils {
    companion object {
        @SuppressLint("QueryPermissionsNeeded")
        fun openFolderInExplorer(context: Context, path: String): Boolean {
            val uri = Uri.parse(path)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "resource/folder")

            return if (intent.resolveActivityInfo(context.packageManager, 0) != null) {
                context.startActivity(intent)
                true
            } else
                false
        }
    }
}