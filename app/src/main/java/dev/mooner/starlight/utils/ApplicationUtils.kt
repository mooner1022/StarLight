package dev.mooner.starlight.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.jakewharton.processphoenix.ProcessPhoenix

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