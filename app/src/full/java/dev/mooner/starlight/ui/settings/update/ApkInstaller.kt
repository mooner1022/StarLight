package dev.mooner.starlight.ui.settings.update

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands a downloaded StarLight APK to the system installer. */
internal fun Context.requestApkInstall(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(intent)
}
