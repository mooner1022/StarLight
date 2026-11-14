/*
 * OnNotificationRemovedEvent.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate

/**
 * A notification was removed; calls the script's `onNotificationRemoved(sbn, rankingMap, reason)` while the app
 * setting **onNotificationPosted 이벤트 사용** (use the onNotificationPosted event) is on.
 *
 * Every app's notifications count, except StarLight's own. The setting is off by default. Called on the project's
 * workers, in projects that are compiled and on.
 *
 * @param sbn The removed notification, as Android's `StatusBarNotification`.
 * @param rankingMap Android's `NotificationListenerService.RankingMap` of the notifications still showing.
 * @param reason Why it was removed: one of Android's `NotificationListenerService.REASON_*` constants, such as
 * `REASON_CANCEL` (2).
 * @see guide:events#notification-events
 * @see guide:app#settings
 */
class OnNotificationRemovedEvent: ProjectEvent() {

    override val id: String = "removed"

    override val name: String = "onNotificationRemoved 이벤트"

    override val description: String = translate {
        Locale.ENGLISH { "When a notification is removed, with the onNotificationPosted setting on" }
        Locale.KOREAN  { "onNotificationPosted 이벤트 사용을 켰을 때, 알림이 지워지면" }
    }

    override val functionName: String = "onNotificationRemoved"

    override val argTypes: Array<CodeGenerator.Argument<*>> =
        arrayOf(
            "sbn" typedAs StatusBarNotification::class,
            "rankingMap" typedAs RankingMap::class,
            "reason" typedAs Int::class,
        )
}
