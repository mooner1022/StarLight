/*
 * Events.kt created by Minki Moon(mooner1022) on 22. 2. 2. 오후 6:08
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.event

import kotlinx.coroutines.CoroutineScope

sealed class Events {

    sealed class Project {

        class ProjectCreateEvent(
            val project: dev.mooner.starlight.plugincore.project.Project,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope

        class ProjectInfoUpdateEvent(
            val project: dev.mooner.starlight.plugincore.project.Project,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope

        class ProjectCompileEvent(
            val project: dev.mooner.starlight.plugincore.project.Project,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope

        class ProjectDeleteEvent(
            val projectName: String,
            val coroutineScope: CoroutineScope = eventCoroutineScope
        ): Event, CoroutineScope by coroutineScope
    }
}