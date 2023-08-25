/*
 * OnNotificationPostedEvent.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class OnNotificationPostedEvent: ProjectEvent() {

    override val id: String = "post"

    override val name: String = "onNotificationPosted 이벤트"

    override val functionName: String = "onNotificationPosted"

    override val argTypes: Array<KClass<*>> =
        arrayOf(StatusBarNotification::class)
}