/*
 * Events.kt created by Minki Moon(mooner1022) on 22. 2. 2. 오후 6:08
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.event

import dev.mooner.starlight.plugincore.logger.LogData
import kotlinx.coroutines.CoroutineScope
import dev.mooner.starlight.plugincore.project.Project as SProject

sealed class Events {

    sealed class Project {

        class ProjectCreateEvent(
            val project: SProject,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope

        class ProjectInfoUpdateEvent(
            val project: SProject,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope

        class ProjectCompileEvent(
            val project: SProject,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope

        class ProjectDeleteEvent(
            val projectName: String,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope
    }

    sealed class Log {

        class LogCreateEvent(
            val log: LogData,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope
    }
}