package dev.mooner.starlight.ui.settings.update

import android.content.Context
import android.net.Uri

/** Google Play builds update only through Google Play, so they never install an APK themselves. */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
internal fun Context.requestApkInstall(uri: Uri): Unit =
    throw UnsupportedOperationException("Google Play builds update through Google Play")
