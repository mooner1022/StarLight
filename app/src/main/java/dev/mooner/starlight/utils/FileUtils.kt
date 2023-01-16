package dev.mooner.starlight.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import java.io.File

fun Context.requestManageStoragePermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")

    startActivity(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            uri
        )
    )
}

fun getLanguageByExtension(ext: String): DefaultEditorActivity.Language? =
    DefaultEditorActivity.Language.values().firstOrNull { ext in it.fileExt }

fun String.toFile(): File =
    File(this)