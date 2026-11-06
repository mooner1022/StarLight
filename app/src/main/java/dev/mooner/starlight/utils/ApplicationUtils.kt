package dev.mooner.starlight.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.jakewharton.processphoenix.ProcessPhoenix
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.ui.splash.SplashActivity

/*
@SuppressLint("QueryPermissionsNeeded")
fun Context.getInstalledApps(): List<ApplicationInfo> =
    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
 */

fun Context.isAppInstalled(packageName: String): Boolean =
    kotlin.runCatching {
        getPackageInfo(packageName)
    }.getOrNull() != null

fun Context.startActivityWithExtra(clazz: Class<*>, extras: Map<String, String> = mapOf()) {
    val intent = Intent(this, clazz).apply {
        if (extras.isNotEmpty())
            extras.forEach(::putExtra)
    }
    startActivity(intent)
}

inline fun <reified T: Activity> Context.startActivity() {
    val intent = Intent(this, T::class.java)
    startActivity(intent)
}

/**
 * For screens that need the runtime. Android brings a screen back in a new process after the app crashed or was
 * killed, and it can come back before the runtime started; then the app starts over from the splash, which waits for
 * it. When this returns true, the caller calls `super.onCreate(null)` and `finish()` and stops there.
 */
fun Activity.restartIfNotReady(): Boolean {
    if (ApplicationSession.isInitComplete)
        return false
    // When the start was aborted, the crash report is on top and the user decides from there.
    if (!GlobalApplication.isStartupAborted)
        startActivity(Intent(this, SplashActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    return true
}

fun Context.restartApplication() {
    /*
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent!!.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
     */
    ProcessPhoenix.triggerRebirth(this)
}

fun Context.getPackageInfo(): PackageInfo =
    getPackageInfo(packageName)

fun Context.getPackageInfo(packageName: String): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
}

fun Context.getAppVersionCode(): Long {
    val pInfo: PackageInfo = getPackageInfo()
    return PackageInfoCompat.getLongVersionCode(pInfo)
}