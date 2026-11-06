/*
 * MessageParserSpec.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.chat

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification

interface MessageParserSpec {

    val id: String

    val name: String

    fun parse(userId: Int, context: Context, sbn: StatusBarNotification, actions: Array<Notification.Action>): Message?

    //fun checkVersion(): String?
}