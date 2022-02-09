package dev.mooner.starlight.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dev.mooner.starlight.BuildConfig
import java.io.File

@Suppress("DEPRECATION")
fun getInternalDirectory() = File(Environment.getExternalStorageDirectory(), "StarLight/")

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