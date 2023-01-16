package dev.mooner.starlight.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

@SuppressLint("QueryPermissionsNeeded")
fun Context.getInstalledApps(): List<ApplicationInfo> =
    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

suspend fun Context.getInstalledAppsAsFlow(): Flow<ApplicationInfo> =
    getInstalledApps().asFlow()

fun Context.isAppInstalled(packageName: String): Boolean =
    kotlin.runCatching {
        packageManager.getPackageInfo(packageName, 0)
    }.getOrNull() != null

fun Context.startActivityWithExtra(clazz: Class<*>, extras: Map<String, String> = mapOf()) {
    val intent = Intent(this, clazz).apply {
        if (extras.isNotEmpty())
            extras.forEach(::putExtra)
    }
    startActivity(intent)
}