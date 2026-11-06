/*
 * OnNotificationPostedEvent.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate

class OnNotificationPostedEvent: ProjectEvent() {

    override val id: String = "post"

    override val name: String = "onNotificationPosted 이벤트"

    override val description: String = translate {
        Locale.ENGLISH { "When any app posts a notification, with the onNotificationPosted setting on" }
        Locale.KOREAN  { "onNotificationPosted 이벤트 사용을 켰을 때, 아무 앱이나 알림을 올리면" }
    }

    override val functionName: String = "onNotificationPosted"

    override val argTypes: Array<CodeGenerator.Argument<*>> =
        arrayOf("sbn" typedAs StatusBarNotification::class)
}