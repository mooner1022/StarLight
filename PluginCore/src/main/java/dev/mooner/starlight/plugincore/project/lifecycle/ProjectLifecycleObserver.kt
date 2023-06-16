/*
 * ProjectLifecycleObserver.kt created by Minki Moon(mooner1022) on 4/21/23, 2:26 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.project.Project

sealed interface ProjectLifecycleObserver {

    interface StateObserver: ProjectLifecycleObserver {

        fun onUpdate(project: Project, state: ProjectLifecycle.State)
    }

    interface ExplicitObserver: ProjectLifecycleObserver {

        fun onCompileStart(project: Project)

        fun onCompileEnd(project: Project)

        fun onEnable(project: Project)

        fun onDisable(project: Project)

        fun onDestroy(project: Project)
    }
}