package dev.mooner.starlight.utils

import android.Manifest
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import java.net.URI
import java.util.concurrent.TimeUnit

private val alphanumericPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
fun randomAlphanumeric(length: Int): String {
    val arr: ArrayList<Char> = arrayListOf()
    repeat(length) {
        arr.add(alphanumericPool.random())
    }
    return arr.joinToString("")
}

fun isForeground(): Boolean {
    val appProcessInfo = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(appProcessInfo)
    return appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE
}

fun String.trimLength(max: Int): String {
    return if (this.length <= max) this else (this.substring(0, max) + "..")
}

fun Context.checkPermissions(permissions: Array<String>): Boolean {
    for (permission in permissions) {
        when (permission) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager())
                    return false
            }
            else -> {
                val perm = ContextCompat.checkSelfPermission(this, permission)
                if (perm == PackageManager.PERMISSION_DENIED) return false
            }
        }
    }
    return true
}

fun formatTime(millis: Long): String {
    val day = 1000 * 60 * 60 * 24
    val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val h = TimeUnit.MILLISECONDS.toHours(millis) % 24
    return if (millis >= day) {
        val d = TimeUnit.MILLISECONDS.toDays(millis)
        String.format("%d일 %d시간 %02d분 %02d초", d, h, m, s)
    } else {
        String.format("%d시간 %02d분 %02d초", h, m, s)
    }
}

fun Context.restartApplication() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent!!.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}

fun Uri.toBitmap(context: Context): Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, this))
} else {
    MediaStore.Images.Media.getBitmap(context.contentResolver, this)
}

fun Uri.toURI(): URI =
    URI(this.toString())

val isDevMode get() = Session.isInitComplete && GlobalConfig.category("dev").getBoolean("dev_mode", false)

const val LAYOUT_DEFAULT = 0
const val LAYOUT_TABLET  = 1
const val LAYOUT_SLIM    = 2

val Context.layoutMode
    get() = resources.getInteger(R.integer.layoutMode).let {
        if (it == LAYOUT_DEFAULT && getScreenSizeDp(this).first <= 320)
            LAYOUT_SLIM
        else
            it
    }

fun Context.getColorCompat(@ColorRes res: Int): Int = ContextCompat.getColor(this, res)

val Context.nightModeFlags get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

fun Context.openWebUrl(url: String) {
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(browserIntent)
}