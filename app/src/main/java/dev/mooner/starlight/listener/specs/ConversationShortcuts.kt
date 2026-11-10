/*
 * ConversationShortcuts.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.specs

import android.app.Notification
import android.app.NotificationChannel
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.text.TextUtils
import androidx.annotation.RequiresApi

/** The conversation shortcut the system keeps with a notification's ranking. */
internal object ConversationShortcuts {

    /** The version Android 11 writes at the start of each ranking in a parcel. */
    private const val ANDROID_R_PARCEL_VERSION = 2

    /** More entries than a list in a ranking can hold: a count above this is not a count. */
    private const val MAX_LIST_SIZE = 1000

    /**
     * The shortcut of the notification with [key] in [rankingMap].
     * Null when the notification has none, when it can't be read, and below Android 11, where rankings have none.
     */
    fun of(rankingMap: RankingMap, key: String): ShortcutInfo? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            Ranking()
                .takeIf { rankingMap.getRanking(key, it) }
                ?.conversationShortcutInfo
        Build.VERSION.SDK_INT == Build.VERSION_CODES.R ->
            try {
                readFromParcel(rankingMap, key)
            } catch (_: RuntimeException) {
                null
            }
        else ->
            null
    }

    /**
     * Android 11 has the shortcut in the ranking but no way to ask for it: its getter is hidden, and blocked
     * even for reflection. A ranking map can be written to a parcel, though, and the shortcut is written with it.
     *
     * This reads that parcel the way Android 11 writes it (`Ranking.writeToParcel`), checking against the keys
     * the map gives that each ranking starts where it should, and gives up when one doesn't: a device whose
     * Android 11 writes rankings differently gets no shortcut rather than a wrong one.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun readFromParcel(rankingMap: RankingMap, key: String): ShortcutInfo? {
        val keys = rankingMap.orderedKeys
        if (key !in keys) return null

        val loader = ShortcutInfo::class.java.classLoader
        val parcel = Parcel.obtain()
        try {
            rankingMap.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            if (parcel.readInt() != keys.size) return null

            for ((index, expected) in keys.withIndex()) {
                if (parcel.readInt() != ANDROID_R_PARCEL_VERSION || parcel.readString() != expected) return null

                parcel.readInt()                                        // rank
                parcel.readInt()                                        // is ambient
                parcel.readInt()                                        // matches the interruption filter
                parcel.readInt()                                        // visibility override
                parcel.readInt()                                        // suppressed visual effects
                parcel.readInt()                                        // importance
                TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel) // importance explanation
                parcel.readString()                                     // override group key
                parcel.readParcelable<NotificationChannel>(loader)      // channel
                repeat(parcel.readCount()) { parcel.readString() }      // override people
                repeat(parcel.readCount()) {                            // snooze criteria
                    if (parcel.readInt() != 0) {
                        parcel.readString()
                        TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel)
                        TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel)
                    }
                }
                parcel.readInt()                                        // shows a badge
                parcel.readInt()                                        // user sentiment
                parcel.readInt()                                        // is hidden
                parcel.readLong()                                       // last audibly alerted
                parcel.readInt()                                        // is noisy
                repeat(parcel.readCount()) {                            // smart actions
                    if (parcel.readInt() != 0)
                        Notification.Action.CREATOR.createFromParcel(parcel)
                }
                repeat(parcel.readCount()) {                            // smart replies
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel)
                }
                parcel.readInt()                                        // can bubble
                parcel.readInt()                                        // is visually interruptive
                parcel.readInt()                                        // is a conversation
                val shortcut = parcel.readParcelable<Parcelable>(loader)
                parcel.readInt()                                        // is a bubble

                if (expected == key) {
                    // What follows has to be the next ranking, or the end, for this one to have been read right.
                    val isLast = index == keys.lastIndex
                    val endsWhereItShould = if (isLast)
                        parcel.dataAvail() == 0
                    else
                        parcel.readInt() == ANDROID_R_PARCEL_VERSION && parcel.readString() == keys[index + 1]
                    return (shortcut as? ShortcutInfo).takeIf { endsWhereItShould }
                }
            }
            return null
        } finally {
            parcel.recycle()
        }
    }

    /** The size a parcel wrote for a list, zero for a null list. */
    private fun Parcel.readCount(): Int {
        val count = readInt()
        check(count <= MAX_LIST_SIZE) { "Not a list size: $count" }
        return count.coerceAtLeast(0)
    }
}
