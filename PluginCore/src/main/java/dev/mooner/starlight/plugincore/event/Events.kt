/*
 * Events.kt created by Minki Moon(mooner1022) on 22. 2. 2. 오후 6:08
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.event

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.logger.LogData
import kotlinx.coroutines.CoroutineScope
import dev.mooner.starlight.plugincore.project.Project as SProject
import dev.mooner.starlight.plugincore.translation.Locale as LocaleEnum

sealed class Events {

    sealed class Notification {

        class Post(
            val statusBarNotification: StatusBarNotification,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope

        class Dismiss(
            val sbn: StatusBarNotification,
            val rankingMap: NotificationListenerService.RankingMap,
            val reason: Int,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope

        class Click(
            val notificationId: Int,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope
    }

    sealed class Project {

        class Create(
            val project: SProject,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope

        class InfoUpdate(
            val project: SProject,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope

        class Compile(
            val project: SProject,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope

        class Delete(
            val projectName: String,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope
    }

    sealed class Log {

        class Create(
            val log: LogData,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope
    }

    sealed class Config {

        class GlobalConfigUpdate(
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope
    }

    sealed class Scheme {

        data class SchemeOpenEvent(
            val params: Map<String, String>,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope
    }

    sealed class Locale {

        /**
         * This event should be only fired from main application, or system can break!
         */
        class Update(
            val locale: LocaleEnum,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope
    }
}