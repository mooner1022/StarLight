/*
 * MessageParserSpec.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.chat

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

interface MessageParserSpec {

    val id: String

    val name: String

    fun parse(userId: Int, context: Context, sbn: StatusBarNotification, actions: Array<Notification.Action>): Message?

    /**
     * Parses [sbn] along with the [ranking] the system gave for it, or null when it gave none.
     * The ranking holds what the notification itself may not, such as its conversation shortcut.
     *
     * @since 1.1.0
     */
    fun parse(
        userId : Int,
        context: Context,
        sbn    : StatusBarNotification,
        actions: Array<Notification.Action>,
        ranking: NotificationListenerService.Ranking?
    ): Message? =
        parse(userId, context, sbn, actions)

    //fun checkVersion(): String?
}