package dev.mooner.starlight.utils

import android.content.Context

fun Context.isKakaoTalkInstalled(): Boolean =
    isAppInstalled(PACKAGE_KAKAO_TALK)

fun Context.getKakaoTalkVersion(): String? {
    if (!isKakaoTalkInstalled())
        return null

    val packageInfo = packageManager.getPackageInfo(PACKAGE_KAKAO_TALK, 0)
    return packageInfo.versionName
}