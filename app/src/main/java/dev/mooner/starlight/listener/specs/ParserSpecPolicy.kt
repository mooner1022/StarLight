/*
 * ParserSpecPolicy.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.specs

import android.os.Build
import dev.mooner.starlight.plugincore.version.Version

/** Which parser spec reads KakaoTalk's chat notifications on a device. */
object ParserSpecPolicy {

    const val DEFAULT               = "default"
    const val ANDROID_R             = "android_r"
    const val CONVERSATION_SHORTCUT = "kakao_26_7"

    /**
     * The first KakaoTalk version whose chat notifications no longer carry the room name:
     * 26.6.3 still sets it as the sub text, 26.7.0 leaves it to the conversation shortcut.
     */
    val ROOM_NAME_LEFT_NOTIFICATION = Version(26, 7, 0)

    /**
     * The ID of the spec for Android [sdkInt] and [kakaoTalkVersion], null when it is unknown.
     * An unknown version gets the newest spec, which still reads what the older one does.
     */
    fun specIdFor(sdkInt: Int, kakaoTalkVersion: Version?): String = when {
        sdkInt < Build.VERSION_CODES.R ->
            DEFAULT
        kakaoTalkVersion != null && kakaoTalkVersion < ROOM_NAME_LEFT_NOTIFICATION ->
            ANDROID_R
        else ->
            CONVERSATION_SHORTCUT
    }

    /** The first of [candidates] that names a room, or [senderName] as a 1:1 chat is named after its sender. */
    fun pickRoomName(senderName: String, vararg candidates: CharSequence?): String =
        candidates.firstNotNullOfOrNull { it?.toString()?.takeIf(String::isNotBlank) }
            ?: senderName
}
